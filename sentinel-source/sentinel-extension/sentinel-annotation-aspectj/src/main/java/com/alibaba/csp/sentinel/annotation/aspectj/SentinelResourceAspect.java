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
package com.alibaba.csp.sentinel.annotation.aspectj;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.lang.reflect.Method;

/**
 * AspctJ 切面
 * Sentinel 基于 AOP 思想，对 @SentinelResource 标记的方法做环绕增强，完成资源的创建。
 * Aspect for methods with {@link SentinelResource} annotation.
 *
 * @author Eric Zhao
 */
@Aspect
public class SentinelResourceAspect extends AbstractSentinelAspectSupport {

    /**
     * 指定切入点为标注了注解 @SentinelResource 的类
     */
    @Pointcut("@annotation(com.alibaba.csp.sentinel.annotation.SentinelResource)")
    public void sentinelResourceAnnotationPointcut() {
    }

    /**
     * 环绕增强（around advice）
     * @param pjp
     * @return
     * @throws Throwable
     */
    @Around("sentinelResourceAnnotationPointcut()")
    public Object invokeResourceWithSentinel(ProceedingJoinPoint pjp) throws Throwable {
        // 获取目标方法
        Method originMethod = resolveMethod(pjp);

        // 获取 @SentinelResource 注解
        SentinelResource annotation = originMethod.getAnnotation(SentinelResource.class);
        if (annotation == null) {
            // Should not go through here.
            throw new IllegalStateException("Wrong state for SentinelResource annotation");
        }

        // 获取注解的 @SentinelResource 的 value 的值，即获取的是资源名称
        String resourceName = getResourceName(annotation.value(), originMethod);
        EntryType entryType = annotation.entryType();
        int resourceType = annotation.resourceType();
        Entry entry = null;

        try {
            /**
             * 创建资源 Entry，即获取资源的操作对象，在目标方法执行之前进行功能增强，即应用流控规则
             * 要织入的、增强的功能
             * resourceName：资源名称，即注解 @SentinelResource 的 value 属性的值
             */
            entry = SphU.entry(resourceName, resourceType, entryType, pjp.getArgs());

            /**
             * 至此流控规则应用完毕，调用目标方法
             */
            Object result = pjp.proceed();

            // 返回结果
            return result;
        } catch (BlockException ex) {
            // 处理 Sentinel 发出的异常，回调 blockHandler 方法
            return handleBlockException(pjp, annotation, ex);
        } catch (Throwable ex) {
            Class<? extends Throwable>[] exceptionsToIgnore = annotation.exceptionsToIgnore();
            // The ignore list will be checked first.
            if (exceptionsToIgnore.length > 0 && exceptionBelongsTo(ex, exceptionsToIgnore)) {
                throw ex;
            }
            if (exceptionBelongsTo(ex, annotation.exceptionsToTrace())) {
                traceException(ex);
                // 处理非 Sentinel 抛出的异常，即处理业务异常，回调 falllback 方法
                return handleFallback(pjp, annotation, ex);
            }

            // No fallback function can handle the exception, so throw it out.
            throw ex;
        } finally {
            if (entry != null) {
                // 清空 entry 中的 context 防止重复调用。(目标方法执行完毕之后，或应用流控规则时未通过后执行）
                entry.exit(1, pjp.getArgs());
            }
        }
    }
}
