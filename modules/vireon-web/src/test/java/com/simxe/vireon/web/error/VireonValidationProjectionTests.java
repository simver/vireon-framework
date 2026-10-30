package com.simxe.vireon.web.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.metadata.ConstraintDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;

class VireonValidationProjectionTests {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void iteratesLongExistingPathWithoutTraversingBusinessObjects() throws Exception {
        Path.Node child = mock(Path.Node.class);
        when(child.getKind()).thenReturn(ElementKind.PROPERTY);
        when(child.getName()).thenReturn("child");
        Path.Node name = mock(Path.Node.class);
        when(name.getKind()).thenReturn(ElementKind.PROPERTY);
        when(name.getName()).thenReturn("name");
        List<Path.Node> nodes = new ArrayList<>(Collections.nCopies(10_000, child));
        nodes.add(name);
        Path path = mock(Path.class);
        when(path.iterator()).thenAnswer(invocation -> nodes.iterator());

        BusinessNode root = mock(BusinessNode.class);
        BusinessNode leaf = mock(BusinessNode.class);
        ConstraintViolation violation = mock(ConstraintViolation.class);
        when(violation.getRootBean()).thenReturn(root);
        when(violation.getLeafBean()).thenReturn(leaf);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("raw message");
        ConstraintDescriptor descriptor = mock(ConstraintDescriptor.class);
        when(descriptor.getAnnotation()).thenReturn(BusinessNode.class.getDeclaredField("name").getAnnotation(NotBlank.class));
        when(violation.getConstraintDescriptor()).thenReturn(descriptor);

        var error = new DefaultMessageSourceResolvable(new String[] {"NotBlank", "NotBlank.java.lang.String"}, "resolved message");
        ParameterValidationResult parameter = mock(ParameterValidationResult.class);
        when(parameter.getContainerIndex()).thenReturn(null);
        when(parameter.getMethodParameter()).thenReturn(new MethodParameter(
                getClass().getDeclaredMethod("endpoint", BusinessNode.class), 0));
        when(parameter.getResolvableErrors()).thenReturn(List.of(error));
        when(parameter.unwrap(error, ConstraintViolation.class)).thenReturn(violation);
        MethodValidationResult result = mock(MethodValidationResult.class);
        when(result.getParameterValidationResults()).thenReturn(List.of(parameter));
        when(result.getCrossParameterValidationResults()).thenReturn(List.of());

        assertThat(VireonValidationFailures.method(result)).containsExactly(new VireonValidationError(
                "child.".repeat(10_000) + "name", "VIREON.VALIDATION.NOT_BLANK", "resolved message"));
        verify(path).iterator();
        verifyNoInteractions(root, leaf);
        verify(violation, never()).getInvalidValue();
    }

    @Test
    void keepsSpringResultFallbackWhenNoConstraintCanBeUnwrapped() throws Exception {
        var error = new DefaultMessageSourceResolvable(new String[] {"Custom.value", "Custom"}, "custom message");
        ParameterValidationResult parameter = mock(ParameterValidationResult.class);
        when(parameter.getContainerIndex()).thenReturn(null);
        when(parameter.getMethodParameter()).thenReturn(new MethodParameter(
                getClass().getDeclaredMethod("endpoint", BusinessNode.class), 0));
        when(parameter.getResolvableErrors()).thenReturn(List.of(error));
        when(parameter.unwrap(error, ConstraintViolation.class)).thenThrow(new IllegalArgumentException("No source"));
        MethodValidationResult result = mock(MethodValidationResult.class);
        when(result.getParameterValidationResults()).thenReturn(List.of(parameter));
        when(result.getCrossParameterValidationResults()).thenReturn(List.of());

        assertThat(VireonValidationFailures.method(result)).containsExactly(
                new VireonValidationError("value", "VIREON.VALIDATION.CUSTOM", "custom message"));
    }

    @Test
    void transparentOptionalDoesNotAcquireACollectionMarker() {
        var field = new org.springframework.validation.FieldError("value", "name", "invalid name");
        var parameter = mock(org.springframework.validation.method.ParameterErrors.class);
        when(parameter.getContainerIndex()).thenReturn(null);
        when(parameter.getContainer()).thenReturn(java.util.Optional.of(new BusinessNode()));
        when(parameter.getAllErrors()).thenReturn(List.of(field));
        var result = mock(MethodValidationResult.class);
        when(result.getParameterValidationResults()).thenReturn(List.of(parameter));
        assertThat(VireonValidationFailures.method(result)).extracting(VireonValidationError::path).containsExactly("name");
    }

    private void endpoint(BusinessNode value) {}

    static class BusinessNode {
        @NotBlank String name;
        BusinessNode getChild() { throw new AssertionError("Projection must not traverse business objects"); }
    }
}
