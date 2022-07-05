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
package com.alibaba.csp.sentinel.slots;

import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.DefaultProcessorSlotChain;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlotChain;
import com.alibaba.csp.sentinel.slotchain.SlotChainBuilder;
import com.alibaba.csp.sentinel.util.SpiLoader;

import java.util.List;

/**
 * Builder for a default {@link ProcessorSlotChain}.
 *
 * @author qinan.qn
 * @author leyou
 */
public class DefaultSlotChainBuilder implements SlotChainBuilder {

    @Override
    public ProcessorSlotChain build() {
        /**
         * 是一个单向节点，默认包含一个节点，且有两个节点 first 与 end 同时指向这个节点
         */
        ProcessorSlotChain chain = new DefaultProcessorSlotChain();

        /**
         * 使用 SPI 机制构建 Slot，会加载 ProcessorSlot 文件中配置的所有 Slot，并且是按照顺序加载：
         * List<ProcessorSlot> sortedSlotList = {NodeSelectorSlot,ClusterBuilderSlot,LogSlot,StatisticSlot,AuthoritySlot,SystemSlot,ParamFlowSlot,FlowSlot,DegradeSlot}
         */
        List<ProcessorSlot> sortedSlotList = SpiLoader.loadPrototypeInstanceListSorted(ProcessorSlot.class);

        for (ProcessorSlot slot : sortedSlotList) {
            if (!(slot instanceof AbstractLinkedProcessorSlot)) {
                RecordLog.warn("The ProcessorSlot(" + slot.getClass().getCanonicalName() + ") is not an instance of AbstractLinkedProcessorSlot, can't be added into ProcessorSlotChain");
                continue;
            }

            // 将 SPI 创建好的所有 slot逐个添加到 SlotChain 中
            chain.addLast((AbstractLinkedProcessorSlot<?>) slot);
        }

        return chain;
    }
}
