package com.colin.aop;

import com.colin.annotation.ColinRateLimiter;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author colin
 * @create 2022-10-04 21:20
 *
 * 使用 google guava 提供的限流器实现限流功能
 */
@Aspect
@Component
@Slf4j
public class AopRateLimiter {

    /**
     * 定义 rateLimiters 限流集合容器
     * key 为限流名称，即每个接口都有自己独立的限流名称
     * value 为 RateLimiter
     */
    private static ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    /**
     * 环绕通知
     * @param pjp
     * @return 目标方法的结果或限流提示
     */
    @Around(value = "@annotation(com.colin.annotation.ColinRateLimiter)")
    public Object currentLimit(ProceedingJoinPoint pjp) throws Throwable {
        // 1.获取拦截到目标方法
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();

        // 2.获取方法
        Method method = methodSignature.getMethod();
        ColinRateLimiter colinRateLimiter = method.getDeclaredAnnotation(ColinRateLimiter.class);

        // 3.获取注解 ColinRateLimiter 中设定的的限流名称
        String rateLimiterName = colinRateLimiter.name();
        if (StringUtils.isEmpty(rateLimiterName)) {
            // 未指定限流名称，则使用方法名作为限流名称，
            rateLimiterName = method.getName();
        }

        // 4.根据限流名称在容器中查找限流器，若不存在，则创建一个新的限流器对象，即创建 RateLimiter 对象
        RateLimiter rateLimiter = rateLimiters.get(rateLimiterName);
        if (rateLimiter == null) {
            // 5.获取到注解 ColinRateLimiter 中设定的 qps 值
            synchronized (this) {
                if (rateLimiter == null) {
                    /**
                     * 获取注解上指定的限流值，即获取指定的 QPS 值，并创建限流器 RateLimiter 对象
                     * RateLimiter：goole guava 提供的限流器
                     */
                    rateLimiter = RateLimiter.create(colinRateLimiter.qps());
                    // 将限流器存放到缓存 rateLimiters 中
                    rateLimiters.put(rateLimiterName, rateLimiter);
                }
            }

        }

        //5.调用限流器的 rateLimiter#tryAcquire() 方法对目标方法实现限流校验
        boolean result = rateLimiter.tryAcquire();
        if (!result) {
            // 6.如果接口被限流，则获取到注解上限流提示
            return colinRateLimiter.msg();
        }
        // 7.放行代码 执行目标方法
        return pjp.proceed();
    }
}
