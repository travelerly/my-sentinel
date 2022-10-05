package com.colin.annotation;

import java.lang.annotation.*;

/**
 * @author colin
 * @create 2022-10-04 21:19
 *
 * Semaphore 实现漏桶算法，通过 AOP 的方式实现线程限流器
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ColinLeakyBucket {

    /**
     * 信号量名称
     * 缓存信号量所使用的 key，value 值为信号量对象
     * @return 信号量名称
     */
    String name() default "";

    /**
     * 限制的线程数量，即许可数量
     * @return 许可数量
     */
    int threads();

    /**
     * @return 限流提示
     */
    String msg() default "服务器繁忙，请稍后重试!";
}
