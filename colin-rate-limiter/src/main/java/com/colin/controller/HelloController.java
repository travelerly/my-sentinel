package com.colin.controller;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.colin.annotation.ColinLeakyBucket;
import com.colin.annotation.ColinRateLimiter;
import com.colin.own.ColinOwnRateLimiter;
import com.colin.own.ColinOwnSemaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author colin
 * @create 2022-10-04 21:14
 */
@RestController
@Slf4j
public class HelloController {

    /**
     * 创建出令牌桶实现的自定义限流器的对象
     * 指定令牌桶的初始容量，即 QPS 限流值
     */
    private ColinOwnRateLimiter rateLimiter = new ColinOwnRateLimiter(2);

    /**
     * 信号量实现线程数限流器
     */
    public ColinOwnSemaphore semaphore = new ColinOwnSemaphore(2);

    /**
     * 使用 goole guava 提供的限流器实现限流功能
     * @return
     */
    @ColinRateLimiter(name = "helloQps",qps = 1,msg = "服务器访问人数过多，请稍后重试!")
    @GetMapping("/helloQps")
    public String helloQps(){
        Entry entry = null;
        try {
            // 限流校验
            entry = SphU.entry("getSentinelQps");
            // 通过校验，执行目标逻辑
            log.info("正常执行目标逻辑代码...");
            return "hello QPS";
        } catch (Exception e) {
            // 被限流，给出限流提示
            log.error("<e:{}>", e);
            return "该接口访问频率过多，请稍后重试!";
        } finally {
            // SphU.entry(xxx) 需要与 entry.exit() 成对出现,否则会导致调用链记录异常
            if (entry != null) {
                entry.exit();
            }
        }
    }

    /**
     * 测试令牌桶实现的QPS限流器
     * @return
     */
    @GetMapping("/helloOwnRateLimiter")
    public String helloOwnRateLimiter(){
        boolean reuslt = rateLimiter.tryAcquire();
        if (!reuslt){
            // 被限流，给出限流提示
            return "当前访问人数过多，请稍后重试!";
        }
        // 未被限流，则执行正常业务逻辑
        log.info("未被限流，则执行正常业务逻辑");
        return "helloOwnRateLimiter";
    }

    /**
     * 测试信号量实现线程数限流器
     * @return
     */
    @GetMapping("/helloSemaphore")
    public String helloSemaphore(){

        // 消费一个许可
        boolean result = semaphore.tryAcquire();

        if (!result){
            log.info("未成功消费到许可，即被限流了");
            return "当前访问人数过多，请稍后重试!";
        }

        try {
            log.info("模拟业务耗时");
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            log.info("释放许可");
            semaphore.release();
        }
        return "helloSemaphore";
    }

    /**
     * Semaphore 实现漏桶算法，通过 AOP 的方式实现线程限流器
     * @return
     */
    @ColinLeakyBucket(name = "helloAopSemaphore",threads = 2,msg = "当前访问人数过多，请稍后重试!")
    @GetMapping("/helloAopSemaphore")
    public String helloAopSemaphore(){
        log.info("正常执行业务逻辑代码...");
        try {
            // 模拟业务逻辑耗时
            Thread.sleep(1200);
        } catch (Exception e) {
        }
        return "helloAopSemaphore";
    }

}
