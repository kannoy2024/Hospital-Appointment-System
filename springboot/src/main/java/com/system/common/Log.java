package com.system.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在 Controller 方法上，由 {@link OperationLogAspect} 自动记录。
 * 例：{@code @Log(module = "用户管理", action = "新增用户")}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    String module() default "";

    String action() default "";
}
