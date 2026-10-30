package com.simxe.vireon.web.error;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedMethod;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/** 请求校验的内部元数据入口；只复用 Spring 的方法/注解解析，不执行校验或保存请求状态。 */
final class VireonHandlerValidationMetadata {

    private static final DefaultParameterNameDiscoverer PARAMETER_NAMES = new DefaultParameterNameDiscoverer();

    private VireonHandlerValidationMetadata() {}

    static AnnotatedMethod requestMethod(Object target, String name, List<Class<?>> parameterTypes) {
        if (target == null
                || !(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
                || !(attributes.getRequest().getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE)
                        instanceof HandlerMethod handler)) {
            return null;
        }
        Class<?> handlerType;
        try {
            // BEST_MATCHING_HANDLER_ATTRIBUTE 可以保存尚以 bean 名称表示的 HandlerMethod。
            Object handlerBean = handler.createWithResolvedBean().getBean();
            Object handlerTarget = singletonTarget(handlerBean);
            Object validationTarget = singletonTarget(target);
            if (handlerTarget == null || handlerTarget != validationTarget) {
                return null;
            }
            handlerType = userType(handlerTarget);
        } catch (BeansException exception) {
            return null;
        }
        Class<?> validationType = userType(target);
        // 已证明实例归属，再核对具体方法；类型相同本身不构成归属证据。
        if (!handlerType.equals(validationType)) {
            return null;
        }
        Method candidate = ReflectionUtils.findMethod(validationType, name, parameterTypes.toArray(Class<?>[]::new));
        if (candidate == null) {
            return null;
        }
        Method current = concreteMethod(handler.getMethod(), handlerType);
        return current.equals(concreteMethod(candidate, validationType)) ? new AnnotatedMethod(current) : null;
    }

    static String parameterName(MethodParameter parameter) {
        MethodParameter merged = parameter;
        if (parameter.getMethod() != null && parameter.getParameterIndex() >= 0) {
            merged = new AnnotatedMethod(parameter.getMethod()).getMethodParameters()[parameter.getParameterIndex()]
                    .withContainingClass(parameter.getContainingClass());
        }
        var annotations = MergedAnnotations.from(merged.getParameterAnnotations());
        for (var type : List.of(RequestParam.class, PathVariable.class, RequestHeader.class,
                CookieValue.class, RequestPart.class, MatrixVariable.class)) {
            var annotation = annotations.get(type);
            if (annotation.isPresent() && !annotation.getString("name").isBlank()) {
                return annotation.getString("name");
            }
        }
        merged.initParameterNameDiscovery(PARAMETER_NAMES);
        String name = merged.getParameterName();
        return name != null ? name : "arg" + merged.getParameterIndex();
    }

    private static Class<?> userType(Object bean) {
        return ClassUtils.getUserClass(AopProxyUtils.ultimateTargetClass(bean));
    }

    private static Object singletonTarget(Object bean) {
        var visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        Object current = bean;
        while (visited.add(current)) {
            Object next = AopProxyUtils.getSingletonTarget(current);
            if (next == null) {
                return current;
            }
            current = next;
        }
        return null;
    }

    private static Method concreteMethod(Method method, Class<?> targetType) {
        return BridgeMethodResolver.findBridgedMethod(AopUtils.getMostSpecificMethod(method, targetType));
    }
}
