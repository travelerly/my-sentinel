package com.colin.controller;

import com.colin.own.ColinOwnRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author colin
 * @create 2022-10-04 22:47
 *
 * 测试令牌桶实现的限流器的功能
 */
//@RestController
@Slf4j
public class ColinOwnRateLimiterController {

    /**
     * 创建出令牌桶实现的自定义限流器的对象
     * 指定令牌桶的初始容量，即 QPS 限流值
     */
    private ColinOwnRateLimiter rateLimiter = new ColinOwnRateLimiter(2);

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
}
