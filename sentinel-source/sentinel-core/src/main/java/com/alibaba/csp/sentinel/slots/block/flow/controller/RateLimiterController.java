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
package com.alibaba.csp.sentinel.slots.block.flow.controller;

import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slots.block.flow.TrafficShapingController;
import com.alibaba.csp.sentinel.util.TimeUtil;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jialiang.linjl
 */
public class RateLimiterController implements TrafficShapingController {

    private final int maxQueueingTimeMs;
    private final double count;

    private final AtomicLong latestPassedTime = new AtomicLong(-1);

    public RateLimiterController(int timeOut, double count) {
        this.maxQueueingTimeMs = timeOut;
        this.count = count;
    }

    @Override
    public boolean canPass(Node node, int acquireCount) {
        return canPass(node, acquireCount, false);
    }

    /**
     * 排队等待模式，采用漏桶算法
     * @param node resource node
     * @param acquireCount count to acquire
     * @param prioritized whether the request is prioritized
     * @return
     */
    @Override
    public boolean canPass(Node node, int acquireCount, boolean prioritized) {
        // Pass when acquire count is less or equal than 0.
        if (acquireCount <= 0) {
            return true;
        }
        // 阈值小于等于 0，阻止请求
        // Reject when count is less or equal than 0.
        // Otherwise,the costTime will be max of long and waitTime will overflow in some cases.
        if (count <= 0) {
            return false;
        }

        // 获取当前时间
        long currentTime = TimeUtil.currentTimeMillis();
        // 计算两次请求之间允许的最小时间间隔。Calculate the interval between every two requests.
        long costTime = Math.round(1.0 * (acquireCount) / count * 1000);

        // 计算本次请求允许执行的时间点 = 最近一次请求的可执行时间 - 两次请求的最小间隔。Expected pass time of this request.
        long expectedTime = costTime + latestPassedTime.get();

        // 如果允许执行时间点小于当前时间，说明可以立即执行
        if (expectedTime <= currentTime) {
            // 更新上一次的请求的执行时间。Contention may exist here, but it's okay.
            latestPassedTime.set(currentTime);
            return true;
        } else {
            /**
             * 不能立即执行，需要计算预期等待时长
             * 预期等待时长 = 两次请求的最小间隔 + 最近一次请求的可执行时间 - 当前时间
             * Calculate the time to wait.
             */
            long waitTime = costTime + latestPassedTime.get() - TimeUtil.currentTimeMillis();
            if (waitTime > maxQueueingTimeMs) {
                // 如果预期等待时间超出阈值，则拒绝请求
                return false;
            } else {
                // 预期等待时间小于阈值，更新最近一次请求的可执行时间，加上 costTime
                long oldTime = latestPassedTime.addAndGet(costTime);
                try {
                    // 再判断一次预期等待时间是否超过阈值
                    waitTime = oldTime - TimeUtil.currentTimeMillis();
                    if (waitTime > maxQueueingTimeMs) {
                        // 如果超过阈值，则把刚才加的时间再减掉
                        latestPassedTime.addAndGet(-costTime);
                        // 返回 false，拒绝请求
                        return false;
                    }
                    // in race condition waitTime may <= 0
                    if (waitTime > 0) {
                        // 预期等待时间再阈值范围内，休眠要等待的时间，醒来后继续执行
                        Thread.sleep(waitTime);
                    }
                    return true;
                } catch (InterruptedException e) {
                }
            }
        }
        return false;
    }

}
