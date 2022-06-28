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
package com.alibaba.csp.sentinel.slots.block.flow.param;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.spi.SpiOrder;

import java.util.List;

/**
 * A processor slot that is responsible for flow control by frequent ("hot spot") parameters.
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 * @since 0.2.0
 */
@SpiOrder(-3000)
public class ParamFlowSlot extends AbstractLinkedProcessorSlot<DefaultNode> {

    /**
     * ParamFlowSlot 负责热点参数限流，是针对进入资源的请求，针对不同的请求参数分别统计 QPS 的限流方式
     * 这里的单机阈值，就是最大令牌数量：macCount
     * 这里的统计窗口时长，就是统计时长：duration
     * 其含义是每个 duration 时间长度内，最多生产 macCount 个令牌
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
        // 如果没有设置热点规则，直接放行
        if (!ParamFlowRuleManager.hasRules(resourceWrapper.getName())) {
            fireEntry(context, resourceWrapper, node, count, prioritized, args);
            return;
        }

        /**
         * 热点规则判断
         * 热点规则判断采用了令牌桶算法来实现参数限流，为每一个不同参数值设置令牌桶，Sentinel 的令牌桶有两部分组成：
         * CacheMap<Object, AtomicLong> tokenCounters = metric == null ? null : metric.getRuleTokenCounter(rule);
         * CacheMap<Object, AtomicLong> timeCounters = metric == null ? null : metric.getRuleTimeCounter(rule);
         * 这两个 map 的 key 都是请求的参数值，value 却不相同：
         * tokenCounters：用来记录剩余令牌数量
         * timeCounters：用来记录上一个请求的时间
         *
         */
        checkFlow(resourceWrapper, count, args);

        // 由 AbstractLinkedProcessorSlot 触发下一个 Slot，FlowSlot
        fireEntry(context, resourceWrapper, node, count, prioritized, args);
    }

    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        fireExit(context, resourceWrapper, count, args);
    }

    void applyRealParamIdx(/*@NonNull*/ ParamFlowRule rule, int length) {
        int paramIdx = rule.getParamIdx();
        if (paramIdx < 0) {
            if (-paramIdx <= length) {
                rule.setParamIdx(length + paramIdx);
            } else {
                // Illegal index, give it a illegal positive value, latter rule checking will pass.
                rule.setParamIdx(-paramIdx);
            }
        }
    }

    void checkFlow(ResourceWrapper resourceWrapper, int count, Object... args) throws BlockException {
        if (args == null) {
            return;
        }
        if (!ParamFlowRuleManager.hasRules(resourceWrapper.getName())) {
            return;
        }
        List<ParamFlowRule> rules = ParamFlowRuleManager.getRulesOfResource(resourceWrapper.getName());

        for (ParamFlowRule rule : rules) {
            applyRealParamIdx(rule, args.length);

            // Initialize the parameter metrics.
            ParameterMetricStorage.initParamMetricsFor(resourceWrapper, rule);

            if (!ParamFlowChecker.passCheck(resourceWrapper, rule, count, args)) {
                String triggeredParam = "";
                if (args.length > rule.getParamIdx()) {
                    Object value = args[rule.getParamIdx()];
                    triggeredParam = String.valueOf(value);
                }
                throw new ParamFlowException(resourceWrapper.getName(), triggeredParam, rule);
            }
        }
    }
}
