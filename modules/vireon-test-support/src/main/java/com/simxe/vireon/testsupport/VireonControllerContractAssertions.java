package com.simxe.vireon.testsupport;

import com.simxe.vireon.web.api.VireonApiResponse;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;

/** 消费者显式选择业务方法进行检查，下载、文本及无响应体端点不在默认扫描范围内。 */
public final class VireonControllerContractAssertions {

    private VireonControllerContractAssertions() {}

    public static void assertExplicitResponse(Method method) {
        Objects.requireNonNull(method, "method must not be null");
        ResolvableType type = ResolvableType.forMethodReturnType(method);
        while (type.resolve() == ResponseEntity.class || type.resolve() == Callable.class) {
            if (type.hasUnresolvableGenerics()) {
                throw invalid(method);
            }
            type = type.getGeneric(0);
        }
        if (type.resolve() != VireonApiResponse.class || type.hasUnresolvableGenerics()) {
            throw invalid(method);
        }
    }

    private static AssertionError invalid(Method method) {
        return new AssertionError("业务方法必须显式声明完整泛型的 VireonApiResponse，可嵌套 ResponseEntity/Callable: " + method);
    }
}
