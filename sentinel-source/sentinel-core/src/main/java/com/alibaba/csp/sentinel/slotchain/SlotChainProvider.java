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
package com.alibaba.csp.sentinel.slotchain;

import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slots.DefaultSlotChainBuilder;
import com.alibaba.csp.sentinel.util.SpiLoader;

/**
 * A provider for creating slot chains via resolved slot chain builder SPI.
 *
 * @author Eric Zhao
 * @since 0.2.0
 */
public final class SlotChainProvider {

    private static volatile SlotChainBuilder slotChainBuilder = null;

    /**
     * The load and pick process is not thread-safe, but it's okay since the method should be only invoked
     * via {@code lookProcessChain} in {@link com.alibaba.csp.sentinel.CtSph} under lock.
     *
     * @return new created slot chain
     */
    public static ProcessorSlotChain newSlotChain() {
        // 若 builder 不为空，则使用 builder 创建 SlotChain，否则先创建一个 builder
        if (slotChainBuilder != null) {
            return slotChainBuilder.build();
        }

        /**
         * 使用 SPI 机制加载 META-INFO 下的 service 目录中所有的 SlotChainBuilder 的实现类
         * 使用 SPI 机制来创建 builder。SlotChainBuilder 为 SPI 接口
         */
        slotChainBuilder = SpiLoader.loadFirstInstanceOrDefault(SlotChainBuilder.class, DefaultSlotChainBuilder.class);

        // 若使用 SPI 机制未能成功创建 builder，则 new 一个 DefaultSlotChainBuilder
        if (slotChainBuilder == null) {
            // Should not go through here.
            RecordLog.warn("[SlotChainProvider] Wrong state when resolving slot chain builder, using default");
            slotChainBuilder = new DefaultSlotChainBuilder();
        } else {
            RecordLog.info("[SlotChainProvider] Global slot chain builder resolved: "
                + slotChainBuilder.getClass().getCanonicalName());
        }

        /**
         * 使用 builder 构建 SlotChain
         * 构建资源的 slot 校验链条，每个资源都有自己独立的校验链条，类似于 Netty 的 pipeline
         */
        return slotChainBuilder.build();
    }

    private SlotChainProvider() {}
}
