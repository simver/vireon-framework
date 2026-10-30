package com.simxe.vireon.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

class VireonValidationIdentityTests {
    @Test
    void resolvesBeanNamesAndRejectsMissingBeans() throws Exception {
        var target = new Target();
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("entry", target);
        var method = Target.class.getMethod("count", int.class);
        withHandler(new HandlerMethod("entry", beans, method), () -> assertThat(resolve(target)).isNotNull());
        var missing = new HandlerMethod("entry", beans, method);
        beans.destroySingleton("entry");
        withHandler(missing, () -> assertThat(resolve(target)).isNull());
    }

    @Test
    void neverObtainsDynamicTargetsOrAcceptsEqualsAsIdentity() throws Exception {
        var target = new Target();
        var factory = new ProxyFactory();
        factory.setTargetSource(new TargetSource() {
            public Class<?> getTargetClass() { return Target.class; }
            public boolean isStatic() { return false; }
            public Object getTarget() { throw new AssertionError("must not obtain dynamic target"); }
            public void releaseTarget(Object value) { throw new AssertionError("must not release dynamic target"); }
        });
        Object proxy = factory.getProxy();
        withHandler(new HandlerMethod(proxy, Target.class.getMethod("count", int.class)),
                () -> assertThat(resolve(target)).isNull());
        withHandler(new HandlerMethod(target, Target.class.getMethod("count", int.class)),
                () -> assertThat(resolve(new Target())).isNull());
    }

    @Test
    void cyclicSingletonProxyFailsClosed() throws Exception {
        var factory = new ProxyFactory(new Target());
        var proxy = factory.getProxy();
        ((org.springframework.aop.framework.Advised) proxy).setTargetSource(
                new org.springframework.aop.target.SingletonTargetSource(proxy));
        withHandler(new HandlerMethod(proxy, Target.class.getMethod("count", int.class)),
                () -> assertThat(resolve(new Target())).isNull());
    }

    private static org.springframework.core.annotation.AnnotatedMethod resolve(Object target) {
        return VireonHandlerValidationMetadata.requestMethod(target, "count", List.of(int.class));
    }

    private static void withHandler(HandlerMethod handler, Runnable assertion) {
        var request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, handler);
        var previous = RequestContextHolder.getRequestAttributes();
        try {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            assertion.run();
        } finally {
            RequestContextHolder.setRequestAttributes(previous);
        }
    }

    public static class Target {
        public void count(int value) {}
        public boolean equals(Object other) { return other instanceof Target; }
        public int hashCode() { return 1; }
    }
}
