package com.colin.annotation;

import java.lang.annotation.*;

/**
 * @author colin
 * @create 2022-10-04 21:19
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ColinRateLimiter {

    /**
     * qps：指定限流值，默认为 1
     * @return 限流值
     */
    double qps() default 1;

    /**
     * 该注解加载  方法上就会独立创建一个 rateLimiter 存放HashMap集合中。
     * key=该方法的名称或者自定义限流的名称 value rateLimiter
     */
    String name() default "";

    /**
     * 限流提示
     * @return
     */
    String msg() default "当前访问的人数过多，请稍后重试!";
}
