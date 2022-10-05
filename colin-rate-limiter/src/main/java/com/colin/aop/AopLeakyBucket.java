package com.colin.aop;

import com.colin.annotation.ColinLeakyBucket;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * @author colin
 * @create 2022-10-05 16:07
 *
 * AOP 的方式实现信号量线程数限流器
 */
@Aspect
@Component
@Slf4j
public class AopLeakyBucket {

    /**
     * 定义信号量缓存容器
     */
    private static ConcurrentHashMap<String, Semaphore> semaphoreMap = new ConcurrentHashMap<>();

    /**
     * 环绕通知
     * @param pjp
     * @return
     */
    @Around(value = "@annotation(com.colin.annotation.ColinLeakyBucket)")
    public Object currentLimit(ProceedingJoinPoint pjp) throws Throwable {
        // 1.获取拦截到目标方法
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        // 2.获取方法
        Method method = methodSignature.getMethod();
        ColinLeakyBucket colinLeakyBucket = method.getDeclaredAnnotation(ColinLeakyBucket.class);
        // 3.获取注解 ColinLeakyBucket 中设定的的限流名称
        String name = colinLeakyBucket.name();
        // 未指定限流名称，则使用方法名作为限流名称，
        String semaphoreName = StringUtils.isEmpty(name) ? method.getName() : name;
        //4.根据限流名称在容器中查找信号量，若不存在，则创建一个新的信号量，即创建 Semaphore 对象
        Semaphore semaphore = semaphoreMap.get(semaphoreName);
        // 5.使用双重检验锁 判断 初始化信号量线程安全性问题
        if (semaphore == null) {
            synchronized (this) {
                if (semaphore == null) {
                    semaphore = new Semaphore(colinLeakyBucket.threads());
                }
                semaphoreMap.put(semaphoreName, semaphore);
            }
        }
        try {
            // 6.调用tryAcquire 如果返回是为false  当前AQS状态=0
            boolean result = semaphore.tryAcquire();
            if (!result) {
                // 返回自定义注解上配置的 返回限流提示
                return colinLeakyBucket.msg();
            }
            //执行目标方法
            Object proceed = pjp.proceed();
            // 被限流的请求 对AQS的状态+1 被限流请求 对AQS状态+1
            semaphore.release();
            return proceed;
        } catch (Exception e) {
            semaphore.release();
            return "系统错误!";
        }
    }
}
