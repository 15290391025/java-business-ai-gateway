package io.github.caoxin.aigateway.autoconfigure;

import io.github.caoxin.aigateway.annotation.AiConfirm;
import io.github.caoxin.aigateway.annotation.AiIntent;
import io.github.caoxin.aigateway.annotation.AiModule;
import io.github.caoxin.aigateway.annotation.AiPermission;
import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.capability.AiCapabilityRegistry;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AnnotationCapabilityScanner implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final AiCapabilityRegistry registry;

    public AnnotationCapabilityScanner(ApplicationContext applicationContext, AiCapabilityRegistry registry) {
        this.applicationContext = applicationContext;
        this.registry = registry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> beanType = applicationContext.getType(beanName);
            if (beanType == null) {
                continue;
            }
            Optional<Class<?>> moduleType = findModuleType(beanType);
            if (moduleType.isEmpty()) {
                continue;
            }

            Object bean = applicationContext.getBean(beanName);
            registerModule(bean, moduleType.get());
        }
    }

    private Optional<Class<?>> findModuleType(Class<?> beanType) {
        AiModule moduleOnClass = AnnotationUtils.findAnnotation(beanType, AiModule.class);
        if (moduleOnClass != null) {
            return Optional.of(beanType);
        }
        return Arrays.stream(beanType.getInterfaces())
            .filter(interfaceType -> AnnotationUtils.findAnnotation(interfaceType, AiModule.class) != null)
            .findFirst();
    }

    private void registerModule(Object bean, Class<?> moduleType) {
        AiModule module = AnnotationUtils.findAnnotation(moduleType, AiModule.class);
        if (module == null) {
            return;
        }

        for (Method method : moduleType.getMethods()) {
            AiIntent intent = AnnotationUtils.findAnnotation(method, AiIntent.class);
            if (intent == null || !intent.enabled()) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                throw new IllegalStateException("@AiIntent 方法必须且只能有一个 Command 参数: " + method);
            }

            AiPermission permission = AnnotationUtils.findAnnotation(method, AiPermission.class);
            AiConfirm confirm = AnnotationUtils.findAnnotation(method, AiConfirm.class);

            registry.register(new AiCapability(
                module.name(),
                module.description(),
                intent.name(),
                intent.description(),
                method.getParameterTypes()[0],
                method.getReturnType(),
                confirm == null ? module.defaultRisk() : confirm.level(),
                confirm != null,
                confirm == null ? 600 : confirm.expireSeconds(),
                confirm == null ? "" : confirm.message(),
                permission == null ? "" : permission.value(),
                bean,
                method,
                List.of(intent.examples()),
                Map.of("declaringType", moduleType.getName())
            ));
        }
    }
}

