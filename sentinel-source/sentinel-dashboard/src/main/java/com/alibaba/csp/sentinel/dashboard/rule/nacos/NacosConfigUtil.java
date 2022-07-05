package com.alibaba.csp.sentinel.dashboard.rule.nacos;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.RuleEntity;
import com.alibaba.fastjson.JSON;

import java.util.List;
import java.util.stream.Collectors;

public final class NacosConfigUtil {

    public static final String GROUP_ID = "SENTINEL_GROUP";
    
    public static final String FLOW_DATA_ID_POSTFIX = "-flow-rules";
    public static final String PARAM_FLOW_DATA_ID_POSTFIX = "-param-flow-rules";
    public static final String DEGRADE_DATA_ID_POSTFIX = "-degrade-rules";
    public static final String SYSTEM_DATA_ID_POSTFIX = "-system-rules";
    public static final String AUTHORITY_DATA_ID_POSTFIX = "-authority-rules";
    public static final String GATEWAY_FLOW_DATA_ID_POSTFIX = "-gateway-flow-rules";
    public static final String GATEWAY_API_DATA_ID_POSTFIX = "-gateway-api-rules";

    public static final String CLUSTER_MAP_DATA_ID_POSTFIX = "-cluster-map";

    /**
     * cc for `cluster-client`
     */
    public static final String CLIENT_CONFIG_DATA_ID_POSTFIX = "-cc-config";
    /**
     * cs for `cluster-server`
     */
    public static final String SERVER_TRANSPORT_CONFIG_DATA_ID_POSTFIX = "-cs-transport-config";
    public static final String SERVER_FLOW_CONFIG_DATA_ID_POSTFIX = "-cs-flow-config";
    public static final String SERVER_NAMESPACE_SET_DATA_ID_POSTFIX = "-cs-namespace-set";
    
    //超时时间
    public static final int READ_TIMEOUT = 3000;

    private NacosConfigUtil() {}
    
    
    /**
     * RuleEntity----->Rule
     * @param entities
     * @return
     */
    public static String convertToRule(List<? extends RuleEntity> entities){
        return JSON.toJSONString(
                entities.stream().map(r -> r.toRule())
                        .collect(Collectors.toList()));
    }
    
}
