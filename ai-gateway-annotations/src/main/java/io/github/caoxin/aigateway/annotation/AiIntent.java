package io.github.caoxin.aigateway.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiIntent {

    String name();

    String description();

    String[] examples() default {};

    boolean enabled() default true;
}

