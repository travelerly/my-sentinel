/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
package com.alibaba.csp.sentinel.slots.block.degrade;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreaker;
import com.alibaba.csp.sentinel.spi.SpiOrder;

import java.util.List;

/**
 * 熔断降级
 * A {@link ProcessorSlot} dedicates to circuit breaking.
 *
 * @author Carpenter Lee
 * @author Eric Zhao
 */
@SpiOrder(-1000)
public class DegradeSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    /**
     * 熔断降级规则判断
     * Sentinel 的降级是基于状态机来实现的
     *
     * @param context         current {@link Context}
     * @param resourceWrapper current resource
     * @param node           generics parameter, usually is a {@link com.alibaba.csp.sentinel.node.Node}
     * @param count           tokens needed
     * @param prioritized     whether the entry is prioritized
     * @param args            parameters of the original call
     * @throws Throwable
     */
    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, DefaultNode node, int count,
                      boolean prioritized, Object... args) throws Throwable {
        // 熔断降级规则判断
        performChecking(context, resourceWrapper);
        // 由 AbstractLinkedProcessorSlot 触发下一个节点（执行目标方法）
        fireEntry(context, resourceWrapper, node, count, prioritized, args);
    }

    void performChecking(Context context, ResourceWrapper r) throws BlockException {
        // 获取到当前资源的所有熔断器 CircuitBreaker
        List<CircuitBreaker> circuitBreakers = DegradeRuleManager.getCircuitBreakers(r.getName());
        if (circuitBreakers == null || circuitBreakers.isEmpty()) {
            // 若熔断器为空，则直接结束
            return;
        }

        // 逐个应用所有熔断器
        for (CircuitBreaker cb : circuitBreakers) {
            // AbstractCircuitBreaker#tryPass()
            if (!cb.tryPass(context)) {
                // 若没有通过当前熔断器，则直接抛出异常
                throw new DegradeException(cb.getRule().getLimitApp(), cb.getRule());
            }
        }
    }

    @Override
    public void exit(Context context, ResourceWrapper r, int count, Object... args) {
        Entry curEntry = context.getCurEntry();
        if (curEntry.getBlockError() != null) {
            fireExit(context, r, count, args);
            return;
        }
        // 获取当前资源的所有熔断器
        List<CircuitBreaker> circuitBreakers = DegradeRuleManager.getCircuitBreakers(r.getName());
        if (circuitBreakers == null || circuitBreakers.isEmpty()) {
            fireExit(context, r, count, args);
            return;
        }

        if (curEntry.getBlockError() == null) {
            // passed request
            for (CircuitBreaker circuitBreaker : circuitBreakers) {
                /**
                 * 两种熔断策略：
                 * ExceptionCircuitBreaker：调用异常数比例判断逻辑
                 * ResponseTimeCircuitBreaker：慢调用比例降级判断逻辑
                 */
                circuitBreaker.onRequestComplete(context);
            }
        }

        fireExit(context, r, count, args);
    }
}
