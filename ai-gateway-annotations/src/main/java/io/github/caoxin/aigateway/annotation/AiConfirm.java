package io.github.caoxin.aigateway.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiConfirm {

    RiskLevel level() default RiskLevel.HIGH;

    String message() default "";

    long expireSeconds() default 600;
}

