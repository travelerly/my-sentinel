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
package com.alibaba.csp.sentinel.command.handler;

import com.alibaba.csp.sentinel.command.CommandHandler;
import com.alibaba.csp.sentinel.command.CommandRequest;
import com.alibaba.csp.sentinel.command.CommandResponse;
import com.alibaba.csp.sentinel.command.annotation.CommandMapping;
import com.alibaba.csp.sentinel.datasource.WritableDataSource;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRule;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.csp.sentinel.util.VersionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;

import java.net.URLDecoder;
import java.util.List;

import static com.alibaba.csp.sentinel.transport.util.WritableDataSourceRegistry.*;

/**
 * 规则设置处理器，用来处理控制台推送过来的规则
 * @author jialiang.linjl
 * @author Eric Zhao
 */
@CommandMapping(name = "setRules", desc = "modify the rules, accept param: type={ruleType}&data={ruleJson}")
public class ModifyRulesCommandHandler implements CommandHandler<String> {
    private static final int FASTJSON_MINIMAL_VER = 0x01020C00;

    @Override
    public CommandResponse<String> handle(CommandRequest request) {
        // XXX from 1.7.2, force to fail when fastjson is older than 1.2.12
        // We may need a better solution on this.
        if (VersionUtil.fromVersionString(JSON.VERSION) < FASTJSON_MINIMAL_VER) {
            // fastjson too old
            return CommandResponse.ofFailure(new RuntimeException("The \"fastjson-" + JSON.VERSION
                    + "\" introduced in application is too old, you need fastjson-1.2.12 at least."));
        }

        // 判断规则类型，例如：流控规则 flow
        String type = request.getParam("type");
        // 规则数据。rule data in get parameter
        String data = request.getParam("data");
        if (StringUtil.isNotEmpty(data)) {
            try {
                data = URLDecoder.decode(data, "utf-8");
            } catch (Exception e) {
                RecordLog.info("Decode rule data error", e);
                return CommandResponse.ofFailure(e, "decode rule data error");
            }
        }

        RecordLog.info("Receiving rule change (type: {}): {}", type, data);

        String result = "success";

        if (FLOW_RULE_TYPE.equalsIgnoreCase(type)) {
            // 流控规则

            // 解析请求数据(控制台推送过来的)
            List<FlowRule> flowRules = JSONArray.parseArray(data, FlowRule.class);
            // 保存(控制台推送过来的)流控规则数据
            FlowRuleManager.loadRules(flowRules);

            /**
             * 是一个持久化的扩展点
             * getFlowDataSource()：获取写数据源
             *                      例如通过实现 WritableDataSourceRegistry#registerFlowDataSource()，就可以持久化流控规则数据
             * writeToDataSource()：持久化流控规则数据，写数据扩展点，可以自定义实现方式，例如写入到文件、nacos 配置中心
             *
             * 例如通过 WritableDataSource 的实现类，将其注册进 WritableDataSourceRegistry 中，
             * 这样控制台将数据推送至客户端时，客户端会先将数据保存至内存中，再将数据持久化到数据源中（例如，文件中，配置中心中）
             */
            if (!writeToDataSource(getFlowDataSource(), flowRules)) {
                result = WRITE_DS_FAILURE_MSG;
            }
            // 向控制台返回结果
            return CommandResponse.ofSuccess(result);
        } else if (AUTHORITY_RULE_TYPE.equalsIgnoreCase(type)) {
            List<AuthorityRule> rules = JSONArray.parseArray(data, AuthorityRule.class);
            AuthorityRuleManager.loadRules(rules);
            if (!writeToDataSource(getAuthorityDataSource(), rules)) {
                result = WRITE_DS_FAILURE_MSG;
            }
            return CommandResponse.ofSuccess(result);
        } else if (DEGRADE_RULE_TYPE.equalsIgnoreCase(type)) {
            List<DegradeRule> rules = JSONArray.parseArray(data, DegradeRule.class);
            DegradeRuleManager.loadRules(rules);
            if (!writeToDataSource(getDegradeDataSource(), rules)) {
                result = WRITE_DS_FAILURE_MSG;
            }
            return CommandResponse.ofSuccess(result);
        } else if (SYSTEM_RULE_TYPE.equalsIgnoreCase(type)) {
            List<SystemRule> rules = JSONArray.parseArray(data, SystemRule.class);
            SystemRuleManager.loadRules(rules);
            if (!writeToDataSource(getSystemSource(), rules)) {
                result = WRITE_DS_FAILURE_MSG;
            }
            return CommandResponse.ofSuccess(result);
        }
        return CommandResponse.ofFailure(new IllegalArgumentException("invalid type"));
    }

    /**
     * Write target value to given data source.
     *
     * @param dataSource writable data source
     * @param value target value to save
     * @param <T> value type
     * @return true if write successful or data source is empty; false if error occurs
     */
    private <T> boolean writeToDataSource(WritableDataSource<T> dataSource, T value) {
        if (dataSource != null) {
            try {
                dataSource.write(value);
            } catch (Exception e) {
                RecordLog.warn("Write data source failed", e);
                return false;
            }
        }
        return true;
    }

    private static final String WRITE_DS_FAILURE_MSG = "partial success (write data source failed)";
    private static final String FLOW_RULE_TYPE = "flow";
    private static final String DEGRADE_RULE_TYPE = "degrade";
    private static final String SYSTEM_RULE_TYPE = "system";
    private static final String AUTHORITY_RULE_TYPE = "authority";
}
