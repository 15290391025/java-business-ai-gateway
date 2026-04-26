package io.github.caoxin.aigateway.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiModule {

    String name();

    String description();

    RiskLevel defaultRisk() default RiskLevel.READ_ONLY;

    String[] tags() default {};
}

