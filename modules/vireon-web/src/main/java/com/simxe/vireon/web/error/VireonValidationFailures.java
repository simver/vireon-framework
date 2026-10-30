package com.simxe.vireon.web.error;

import static com.simxe.vireon.web.error.VireonHandlerValidationMetadata.parameterName;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedMethod;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;

/** 校验入口的内部适配：先判断责任，再转换可公开明细；不负责 HTTP 输出。 */
final class VireonValidationFailures {

    private VireonValidationFailures() {}

    static List<VireonValidationError> binding(List<ObjectError> errors) {
        return errors.stream().map(error -> detail(
                error instanceof FieldError field ? field.getField() : error.getObjectName(), error)).toList();
    }

    static List<VireonValidationError> method(MethodValidationResult result) {
        List<VireonValidationError> errors = new ArrayList<>();
        for (var parameter : result.getParameterValidationResults()) {
            String location = containerLocation(parameter);
            if (parameter instanceof ParameterErrors bean) {
                // Spring 的字段路径相对于当前元素；元素位置保存在外层参数结果，不能在展开时丢弃。
                for (ObjectError error : bean.getAllErrors()) {
                    String path = error instanceof FieldError field
                            ? location.isEmpty() ? field.getField() : location + "." + field.getField()
                            : location.isEmpty() ? error.getObjectName() : location;
                    errors.add(detail(path, error));
                }
            } else {
                String path = parameterName(parameter.getMethodParameter()) + location;
                for (var error : parameter.getResolvableErrors()) {
                    ConstraintViolation<?> violation = unwrap(parameter, error);
                    errors.add(violation == null ? detail(path, error)
                            : constraint(violation, parameter.getMethodParameter(), error.getDefaultMessage()));
                }
            }
        }
        result.getCrossParameterValidationResults().forEach(error -> errors.add(detail("<cross-parameter>", error)));
        return List.copyOf(errors);
    }

    private static String containerLocation(ParameterValidationResult parameter) {
        if (parameter.getContainerIndex() != null) {
            return "[" + parameter.getContainerIndex() + "]";
        }
        if (parameter.getContainerKey() != null) {
            return "[" + parameter.getContainerKey() + "]";
        }
        // Set 等 Collection 有元素层级但没有位置；Optional 等透明容器不人为增加 []。
        return parameter.getContainer() instanceof java.util.Collection<?> ? "[]" : "";
    }

    static boolean isRequestMethod(MethodValidationResult result) {
        return !result.isForReturnValue() && VireonHandlerValidationMetadata.requestMethod(
                result.getTarget(), result.getMethod().getName(), List.of(result.getMethod().getParameterTypes())) != null;
    }

    static boolean isRequestInput(ConstraintViolationException exception) {
        return !exception.getConstraintViolations().isEmpty()
                && exception.getConstraintViolations().stream().allMatch(violation -> inputParameter(violation) != null);
    }

    static List<VireonValidationError> constraints(ConstraintViolationException exception) {
        return exception.getConstraintViolations().stream().map(violation ->
                constraint(violation, inputParameter(violation), violation.getMessage())).toList();
    }

    private static ConstraintViolation<?> unwrap(ParameterValidationResult parameter, MessageSourceResolvable error) {
        try {
            return parameter.unwrap(error, ConstraintViolation.class);
        } catch (IllegalArgumentException exception) {
            // 非 Bean Validation 的结果可能没有原始约束；保留其已有 Spring 错误转换。
            return null;
        }
    }

    private static VireonValidationError constraint(
            ConstraintViolation<?> violation, MethodParameter parameter, String message) {
        StringBuilder path = new StringBuilder();
        boolean crossParameter = false;
        // 只迭代校验器已产生的路径；不访问业务属性、不递归遍历对象、不重新执行校验。
        for (Path.Node node : violation.getPropertyPath()) {
            crossParameter |= node.getKind() == ElementKind.CROSS_PARAMETER;
            if (node.getKind() == ElementKind.PROPERTY || node.getKind() == ElementKind.CONTAINER_ELEMENT
                    || node.getKind() == ElementKind.BEAN) {
                if (node.isInIterable()) {
                    path.append('[').append(node.getIndex() != null ? node.getIndex()
                            : node.getKey() != null ? node.getKey() : "").append(']');
                }
                if (node.getKind() == ElementKind.PROPERTY) {
                    if (!path.isEmpty()) {
                        path.append('.');
                    }
                    path.append(node.getName());
                }
            }
        }
        String resolvedPath = !path.isEmpty() ? path.toString() : crossParameter ? "<cross-parameter>"
                : violation.getLeafBean() != violation.getRootBean()
                        ? Conventions.getVariableNameForParameter(parameter) : parameterName(parameter);
        if (resolvedPath.startsWith("[") && violation.getLeafBean() == violation.getRootBean()) {
            resolvedPath = parameterName(parameter) + resolvedPath;
        }
        return new VireonValidationError(resolvedPath,
                code(violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()),
                message == null ? violation.getMessage() : message);
    }

    private static MethodParameter inputParameter(ConstraintViolation<?> violation) {
        AnnotatedMethod method = null;
        int index = -1;
        boolean crossParameter = false;
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.RETURN_VALUE || node.getKind() == ElementKind.CONSTRUCTOR) {
                return null;
            }
            if (node.getKind() == ElementKind.METHOD) {
                method = VireonHandlerValidationMetadata.requestMethod(violation.getRootBean(), node.getName(),
                        node.as(Path.MethodNode.class).getParameterTypes());
            } else if (node.getKind() == ElementKind.PARAMETER) {
                index = node.as(Path.ParameterNode.class).getParameterIndex();
            } else if (node.getKind() == ElementKind.CROSS_PARAMETER) {
                crossParameter = true;
            }
        }
        if (method == null || (!crossParameter && (index < 0 || index >= method.getMethodParameters().length))) {
            return null;
        }
        return crossParameter ? method.getReturnType() : method.getMethodParameters()[index];
    }

    private static VireonValidationError detail(String path, MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        String constraint = error instanceof ObjectError object ? object.getCode()
                : codes == null || codes.length == 0 ? null : codes[codes.length - 1];
        return new VireonValidationError(path, code(constraint),
                error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage());
    }

    private static String code(String constraint) {
        return "VIREON.VALIDATION." + (constraint == null || constraint.isBlank() ? "INVALID"
                : constraint.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT));
    }
}
