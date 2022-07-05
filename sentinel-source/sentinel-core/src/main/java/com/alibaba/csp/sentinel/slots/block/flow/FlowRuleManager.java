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
package com.alibaba.csp.sentinel.slots.block.flow;

import com.alibaba.csp.sentinel.concurrent.NamedThreadFactory;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.node.metric.MetricTimerListener;
import com.alibaba.csp.sentinel.property.DynamicSentinelProperty;
import com.alibaba.csp.sentinel.property.PropertyListener;
import com.alibaba.csp.sentinel.property.SentinelProperty;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 流控规则管理器
 * <p>
 * One resources can have multiple rules. And these rules take effects in the following order:
 * <ol>
 * <li>requests from specified caller</li>
 * <li>no specified caller</li>
 * </ol>
 * </p>
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 */
public class FlowRuleManager {

    /**
     * 保存所有的流控规则
     */
    private static final Map<String, List<FlowRule>> flowRules = new ConcurrentHashMap<String, List<FlowRule>>();

    /**
     * 流控规则监听器
     */
    private static final FlowPropertyListener LISTENER = new FlowPropertyListener();
    private static SentinelProperty<List<FlowRule>> currentProperty = new DynamicSentinelProperty<List<FlowRule>>();

    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1,
        new NamedThreadFactory("sentinel-metrics-record-task", true));

    static {
        currentProperty.addListener(LISTENER);
        SCHEDULER.scheduleAtFixedRate(new MetricTimerListener(), 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Listen to the {@link SentinelProperty} for {@link FlowRule}s. The property is the source of {@link FlowRule}s.
     * Flow rules can also be set by {@link #loadRules(List)} directly.
     *
     * @param property the property to listen.
     */
    public static void register2Property(SentinelProperty<List<FlowRule>> property) {
        AssertUtil.notNull(property, "property cannot be null");
        /**
         * 设置读规则数据源监听器
         */
        synchronized (LISTENER) {
            RecordLog.info("[FlowRuleManager] Registering new property to flow rule manager");
            currentProperty.removeListener(LISTENER);
            // 注册监听器，用于监听流控规则数据的变更
            property.addListener(LISTENER);
            currentProperty = property;
        }
    }

    /**
     * 获取流控规则数据
     * Get a copy of the rules.
     *
     * @return a new copy of the rules.
     */
    public static List<FlowRule> getRules() {
        List<FlowRule> rules = new ArrayList<FlowRule>();
        for (Map.Entry<String, List<FlowRule>> entry : flowRules.entrySet()) {
            rules.addAll(entry.getValue());
        }
        return rules;
    }

    /**
     * 加载流控规则
     * Load {@link FlowRule}s, former rules will be replaced.
     *
     * @param rules new rules to load.
     */
    public static void loadRules(List<FlowRule> rules) {
        currentProperty.updateValue(rules);
    }

    static Map<String, List<FlowRule>> getFlowRuleMap() {
        return flowRules;
    }

    public static boolean hasConfig(String resource) {
        return flowRules.containsKey(resource);
    }

    public static boolean isOtherOrigin(String origin, String resourceName) {
        if (StringUtil.isEmpty(origin)) {
            return false;
        }

        List<FlowRule> rules = flowRules.get(resourceName);

        if (rules != null) {
            for (FlowRule rule : rules) {
                if (origin.equals(rule.getLimitApp())) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 流控规则监听器
     */
    private static final class FlowPropertyListener implements PropertyListener<List<FlowRule>> {

        /**
         * 监听器监听变更回调方法，数据发生变更，回调此方法，更新数据，先清空原有数据，在重新添加新数据
         * @param value updated value.
         */
        @Override
        public void configUpdate(List<FlowRule> value) {
            Map<String, List<FlowRule>> rules = FlowRuleUtil.buildFlowRuleMap(value);
            if (rules != null) {
                /**
                 * 流控规则数据发生变更，清空内存中原有缓存
                 */
                flowRules.clear();
                /**
                 * 重新加载流控规则数据，将数据保存在内存中
                 */
                flowRules.putAll(rules);
            }
            RecordLog.info("[FlowRuleManager] Flow rules received: " + flowRules);
        }

        @Override
        public void configLoad(List<FlowRule> conf) {
            Map<String, List<FlowRule>> rules = FlowRuleUtil.buildFlowRuleMap(conf);
            if (rules != null) {
                flowRules.clear();
                flowRules.putAll(rules);
            }
            RecordLog.info("[FlowRuleManager] Flow rules loaded: " + flowRules);
        }
    }

}
