package io.github.caoxin.aigateway.autoconfigure;

import io.github.caoxin.aigateway.annotation.AiConfirm;
import io.github.caoxin.aigateway.annotation.AiIntent;
import io.github.caoxin.aigateway.annotation.AiModule;
import io.github.caoxin.aigateway.annotation.AiPermission;
import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.capability.AiCapabilityRegistry;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
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
            Object bean = applicationContext.getBean(beanName);
            Class<?> targetType = AopUtils.getTargetClass(bean);
            if (targetType == null) {
                continue;
            }

            List<Class<?>> moduleTypes = findModuleTypes(targetType);
            if (moduleTypes.isEmpty()) {
                continue;
            }

            for (Class<?> moduleType : moduleTypes) {
                registerModule(bean, targetType, moduleType);
            }
        }
    }

    private List<Class<?>> findModuleTypes(Class<?> beanType) {
        AiModule moduleOnClass = AnnotationUtils.findAnnotation(beanType, AiModule.class);
        if (moduleOnClass != null) {
            return List.of(beanType);
        }

        return Arrays.stream(ClassUtils.getAllInterfacesForClass(beanType))
            .filter(interfaceType -> AnnotationUtils.findAnnotation(interfaceType, AiModule.class) != null)
            .distinct()
            .toList();
    }

    private void registerModule(Object bean, Class<?> targetType, Class<?> moduleType) {
        AiModule module = AnnotationUtils.findAnnotation(moduleType, AiModule.class);
        if (module == null) {
            return;
        }

        for (Method method : moduleType.getMethods()) {
            Method targetMethod = findTargetMethod(targetType, method).orElse(method);
            AiIntent intent = findMethodAnnotation(method, targetMethod, AiIntent.class);
            if (intent == null || !intent.enabled()) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                throw new IllegalStateException("@AiIntent 方法必须且只能有一个 Command 参数: " + method);
            }

            AiPermission permission = findMethodAnnotation(method, targetMethod, AiPermission.class);
            AiConfirm confirm = findMethodAnnotation(method, targetMethod, AiConfirm.class);

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
                Map.of(
                    "declaringType", moduleType.getName(),
                    "targetType", targetType.getName()
                )
            ));
        }
    }

    private Optional<Method> findTargetMethod(Class<?> targetType, Method moduleMethod) {
        try {
            return Optional.of(targetType.getMethod(moduleMethod.getName(), moduleMethod.getParameterTypes()));
        } catch (NoSuchMethodException exception) {
            try {
                return Optional.of(targetType.getDeclaredMethod(moduleMethod.getName(), moduleMethod.getParameterTypes()));
            } catch (NoSuchMethodException ignored) {
                return Optional.empty();
            }
        }
    }

    private <A extends Annotation> A findMethodAnnotation(
        Method moduleMethod,
        Method targetMethod,
        Class<A> annotationType
    ) {
        A annotation = AnnotationUtils.findAnnotation(moduleMethod, annotationType);
        if (annotation != null) {
            return annotation;
        }
        return AnnotationUtils.findAnnotation(targetMethod, annotationType);
    }
}
