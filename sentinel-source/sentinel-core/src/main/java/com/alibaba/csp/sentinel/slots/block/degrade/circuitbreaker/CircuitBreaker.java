/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;

/**
 * <p>Basic <a href="https://martinfowler.com/bliki/CircuitBreaker.html">circuit breaker</a> interface.</p>
 *
 * @author Eric Zhao
 *
 * sentinel 1.8 中，将三种熔断策略（满调用、异常比例、异常数）封装为两种熔断器：
 * 1. 响应时间熔断器
 * 2. 异常熔断器
 */
public interface CircuitBreaker {

    /**
     * 获取降级规则。Get the associated circuit breaking rule.
     *
     * @return associated circuit breaking rule
     */
    DegradeRule getRule();

    /**
     * 判断请求是否可以通过，true：表示通过，不用降级；false：表示未通过，需降级处理
     * Acquires permission of an invocation only if it is available at the time of invoking.
     *
     * @param context context of current invocation
     * @return {@code true} if permission was acquired and {@code false} otherwise
     */
    boolean tryPass(Context context);

    /**
     * 获取当前熔断器状态
     * Get current state of the circuit breaker.
     *
     * @return current state of the circuit breaker
     */
    State currentState();

    /**
     * 回调方法，当请求通过并完成后会触发此方法
     * <p>Record a completed request with the context and handle state transformation of the circuit breaker.</p>
     * <p>Called when a <strong>passed</strong> invocation finished.</p>
     *
     * @param context context of current invocation
     */
    void onRequestComplete(Context context);

    /**
     * Circuit breaker state.
     */
    enum State {
        /**
         * 打开状态，会拒绝所有请求
         * In {@code OPEN} state, all requests will be rejected until the next recovery time point.
         */
        OPEN,

        /**
         * 过度状态
         * In {@code HALF_OPEN} state, the circuit breaker will allow a "probe" invocation.
         * If the invocation is abnormal according to the strategy (e.g. it's slow), the circuit breaker
         * will re-transform to the {@code OPEN} state and wait for the next recovery time point;
         * otherwise the resource will be regarded as "recovered" and the circuit breaker
         * will cease cutting off requests and transform to {@code CLOSED} state.
         */
        HALF_OPEN,

        /**
         * 关闭状态，所有请求都可以通过
         * In {@code CLOSED} state, all requests are permitted. When current metric value exceeds the threshold,
         * the circuit breaker will transform to {@code OPEN} state.
         */
        CLOSED
    }
}
