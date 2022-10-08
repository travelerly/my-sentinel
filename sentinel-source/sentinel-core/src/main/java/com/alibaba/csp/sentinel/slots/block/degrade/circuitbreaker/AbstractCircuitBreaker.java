/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.util.function.BiConsumer;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Eric Zhao
 * @since 1.8.0
 */
public abstract class AbstractCircuitBreaker implements CircuitBreaker {

    protected final DegradeRule rule;
    protected final int recoveryTimeoutMs;

    private final EventObserverRegistry observerRegistry;

    protected final AtomicReference<State> currentState = new AtomicReference<>(State.CLOSED);
    protected volatile long nextRetryTimestamp;

    public AbstractCircuitBreaker(DegradeRule rule) {
        this(rule, EventObserverRegistry.getInstance());
    }

    AbstractCircuitBreaker(DegradeRule rule, EventObserverRegistry observerRegistry) {
        AssertUtil.notNull(observerRegistry, "observerRegistry cannot be null");
        if (!DegradeRuleManager.isValidRule(rule)) {
            throw new IllegalArgumentException("Invalid DegradeRule: " + rule);
        }
        this.observerRegistry = observerRegistry;
        this.rule = rule;
        this.recoveryTimeoutMs = rule.getTimeWindow() * 1000;
    }

    @Override
    public DegradeRule getRule() {
        return rule;
    }

    @Override
    public State currentState() {
        return currentState.get();
    }

    @Override
    public boolean tryPass(Context context) {
        // Template implementation.
        if (currentState.get() == State.CLOSED) {
            // 熔断器为关闭状态，则请求可以直接通过
            return true;
        }

        /**
         * 熔断器状态为打开状态，此时再查看：
         * retryTimeoutArrived()：计算当前时间是否超过了上次熔断时间 + 熔断窗口时间，
         * fromOpenToHalfOpen(context)：采用 CAS 的方式将断路器由 OPEN 状态改为 HALF_OPEN状态，即表示可以尝试性发送请求
         * 若当前时间已经超过了上次熔断的窗口时间，则表示熔断结束，则尝试将断路器由 OPEN 状态改为 HALF_OPEN 状态
         * 修改成功返回 true，否则返回 false
         */
        if (currentState.get() == State.OPEN) {
            // For half-open state we allow a request for probing.
            return retryTimeoutArrived() && fromOpenToHalfOpen(context);
        }

        // open 状态，并且时间窗为未到，则返回 false
        return false;
    }

    /**
     * Reset the statistic data.
     */
    abstract void resetStat();

    protected boolean retryTimeoutArrived() {
        // 当前时间 大于 下一次 HalfOpen 的重试时间
        return TimeUtil.currentTimeMillis() >= nextRetryTimestamp;
    }

    protected void updateNextRetryTimestamp() {
        this.nextRetryTimestamp = TimeUtil.currentTimeMillis() + recoveryTimeoutMs;
    }

    protected boolean fromCloseToOpen(double snapshotValue) {
        State prev = State.CLOSED;
        // 使用 CAS 的方式则将熔断器的状态由 CLOSED 转为 OPEN 状态
        if (currentState.compareAndSet(prev, State.OPEN)) {
            // 计算熔断窗口时间
            updateNextRetryTimestamp();

            notifyObservers(prev, State.OPEN, snapshotValue);
            return true;
        }
        return false;
    }

    protected boolean fromOpenToHalfOpen(Context context) {
        // 使用 CAS 的方式则将熔断器的状态由 OPEN 转为 HALF_OPEN 状态
        if (currentState.compareAndSet(State.OPEN, State.HALF_OPEN)) {
            // 状态变更的事件通知
            notifyObservers(State.OPEN, State.HALF_OPEN, null);
            // 获取到当前的资源
            Entry entry = context.getCurEntry();
            // 给资源设置监听器，在资源 Entry 销毁时(资源业务执行完毕时)触发
            entry.whenTerminate(new BiConsumer<Context, Entry>() {
                @Override
                public void accept(Context context, Entry entry) {
                    // Note: This works as a temporary workaround for https://github.com/alibaba/Sentinel/issues/1638
                    // Without the hook, the circuit breaker won't recover from half-open state in some circumstances
                    // when the request is actually blocked by upcoming rules (not only degrade rules).
                    // 判断资源业务是否正常
                    if (entry.getBlockError() != null) {
                        // Fallback to OPEN due to detecting request is blocked
                        // 如果资源业务异常，通过 CAS 将状态由 OPEN 修改为 HALF_OPEN。修改成功，则返回 true，否则返回 false。
                        currentState.compareAndSet(State.HALF_OPEN, State.OPEN);
                        notifyObservers(State.HALF_OPEN, State.OPEN, 1.0d);
                    }
                }
            });
            return true;
        }
        return false;
    }
    
    private void notifyObservers(CircuitBreaker.State prevState, CircuitBreaker.State newState, Double snapshotValue) {
        for (CircuitBreakerStateChangeObserver observer : observerRegistry.getStateChangeObservers()) {
            observer.onStateChange(prevState, newState, rule, snapshotValue);
        }
    }

    protected boolean fromHalfOpenToOpen(double snapshotValue) {
        // 使用 CAS 的方式则将熔断器的状态由 HALF_OPEN 转为 OPEN 状态
        if (currentState.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            // 熔断器再次被打开，则重置设置熔断器的熔断周期
            updateNextRetryTimestamp();
            notifyObservers(State.HALF_OPEN, State.OPEN, snapshotValue);
            return true;
        }
        return false;
    }

    protected boolean fromHalfOpenToClose() {
        // 使用 CAS 的方式则将熔断器的状态由 HALF_OPEN 转为 CLOSED 状态
        if (currentState.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            // 熔断器关闭，重置熔断器慢调用总和统计数为 0
            resetStat();
            notifyObservers(State.HALF_OPEN, State.CLOSED, null);
            return true;
        }
        return false;
    }

    protected void transformToOpen(double triggerValue) {
        State cs = currentState.get();
        switch (cs) {
            case CLOSED:
                fromCloseToOpen(triggerValue);
                break;
            case HALF_OPEN:
                fromHalfOpenToOpen(triggerValue);
                break;
            default:
                break;
        }
    }
}
