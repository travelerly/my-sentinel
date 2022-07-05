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
package com.alibaba.csp.sentinel.slots.statistic;

/**
 * 数据统计的维度
 * @author Eric Zhao
 */
public enum MetricEvent {

    /**
     * 通过所有校验规则。Normal pass.
     */
    PASS,

    /**
     * 没有通过校验规则，抛出 BlockException 的调用。Normal block.
     */
    BLOCK,

    /**
     * 发生了正常的业务异常的调用
     */
    EXCEPTION,

    /**
     * 调用完成的情况，不管是否抛出了异常
     */
    SUCCESS,

    /**
     * 所有 SUCCESS 调用耗费的总时间
     */
    RT,

    /**
     * 未来使用，预占用。Passed in future quota (pre-occupied, since 1.5.0).
     */
    OCCUPIED_PASS
}
