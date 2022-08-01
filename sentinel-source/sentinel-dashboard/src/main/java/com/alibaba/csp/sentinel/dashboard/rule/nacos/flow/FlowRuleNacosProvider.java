package com.alibaba.csp.sentinel.dashboard.rule.nacos.flow;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.FlowRuleEntity;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.nacos.NacosConfigUtil;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ⽤于从数据中⼼拉取数据
 * @author colin
 * @create 2022-07-05 11:39
 */
@Component("flowRuleNacosProvider")
public class FlowRuleNacosProvider implements DynamicRuleProvider<List<FlowRuleEntity>> {

    @Autowired
    private ConfigService configService;

    @Override
    public List<FlowRuleEntity> getRules(String appName,String ip,Integer port) throws NacosException {

        // 从 nacos 配置中心拉取配置
        String rules = configService.getConfig(appName + NacosConfigUtil.FLOW_DATA_ID_POSTFIX,
                NacosConfigUtil.GROUP_ID, NacosConfigUtil.READ_TIMEOUT);

        if (StringUtil.isEmpty(rules)){
            return new ArrayList<>();
        }

        // 解析 json 获取到 List<FlowRule>
        List<FlowRule> list = JSON.parseArray(rules, FlowRule.class);

        // 客户端规则实体是：FlowRule ===> 控制台规则实体是：FlowRuleEntity
        return list
                .stream()
                .map(rule -> FlowRuleEntity.fromFlowRule(appName,ip,port,rule))
                .collect(Collectors.toList());
    }
}
