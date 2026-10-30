package com.simxe.vireon.web.error;

import com.simxe.vireon.core.error.VireonCommonErrorCode;
import com.simxe.vireon.core.error.VireonErrorCode;
import com.simxe.vireon.core.error.VireonException;
import com.simxe.vireon.web.api.VireonApiResponse;
import com.simxe.vireon.web.context.VireonRequestTraceId;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.validation.BindException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class VireonGlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(VireonGlobalExceptionHandler.class);

    @ExceptionHandler(VireonException.class)
    public ResponseEntity<VireonApiResponse<Void>> handleVireonException(VireonException exception) {
        if (VireonErrorResponses.cannotWriteFailure(exception)) {
            return null;
        }
        VireonErrorCode errorCode = exception.errorCode();
        String message = exception.getMessage() == null ? errorCode.message() : exception.getMessage();
        return VireonErrorResponses.failure(errorCode, message, null, HttpHeaders.EMPTY);
    }

    // 保留既有单参数调用签名；标准 MVC 异常的运行时注册由父类拥有。
    public ResponseEntity<VireonApiResponse<List<VireonValidationError>>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        return validationResponse(VireonValidationFailures.binding(exception.getBindingResult().getAllErrors()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<VireonApiResponse<List<VireonValidationError>>> handleBindException(
            BindException exception) {
        if (VireonErrorResponses.cannotWriteFailure(exception)) {
            return null;
        }
        return validationResponse(VireonValidationFailures.binding(exception.getBindingResult().getAllErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<? extends VireonApiResponse<?>> handleConstraintViolation(
            ConstraintViolationException exception) {
        if (VireonErrorResponses.cannotWriteFailure(exception)) {
            return null;
        }
        return VireonValidationFailures.isRequestInput(exception)
                ? validationResponse(VireonValidationFailures.constraints(exception)) : internalFailure(exception);
    }

    public ResponseEntity<? extends VireonApiResponse<?>> handleMethodValidation(MethodValidationException exception) {
        return VireonValidationFailures.isRequestMethod(exception)
                ? validationResponse(VireonValidationFailures.method(exception)) : internalFailure(exception);
    }

    public ResponseEntity<VireonApiResponse<Void>> handleBadRequest(Exception exception) {
        return failure(VireonCommonErrorCode.BAD_REQUEST);
    }

    public ResponseEntity<VireonApiResponse<Void>> handleServletRequestBinding(
            ServletRequestBindingException exception) {
        return exception.getStatusCode().is4xxClientError()
                ? failure(VireonCommonErrorCode.BAD_REQUEST)
                : internalFailure(exception);
    }

    public ResponseEntity<? extends VireonApiResponse<?>> handleHandlerMethodValidation(
            HandlerMethodValidationException exception) {
        return exception.isForReturnValue()
                ? internalFailure(exception)
                : validationResponse(VireonValidationFailures.method(exception));
    }

    public ResponseEntity<VireonApiResponse<Void>> handleNotFound(Exception exception) {
        return failure(VireonCommonErrorCode.NOT_FOUND);
    }

    public ResponseEntity<VireonApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception) {
        return failure(VireonCommonErrorCode.METHOD_NOT_ALLOWED, exception.getHeaders());
    }

    public ResponseEntity<VireonApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception) {
        return failure(VireonCommonErrorCode.UNSUPPORTED_MEDIA_TYPE, exception.getHeaders());
    }

    public ResponseEntity<Void> handleNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        return VireonErrorResponses.notAcceptable();
    }

    private static ResponseEntity<VireonApiResponse<Void>> handleErrorResponse(ErrorResponse errorResponse) {
        VireonErrorCode errorCode = errorCodeFor(errorResponse.getStatusCode());
        return failure(errorCode, errorResponse.getHeaders());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<VireonApiResponse<Void>> handleException(Exception exception) throws Exception {
        if (VireonErrorResponses.cannotWriteFailure(exception)) {
            LOGGER.warn("Response cannot be rewritten, traceId={}", VireonRequestTraceId.currentTraceId(), exception);
            return null;
        }
        if (exception instanceof ErrorResponse errorResponse) {
            return handleErrorResponse(errorResponse);
        }
        return internalFailure(exception);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (VireonErrorResponses.cannotWriteFailure(exception)) {
            return null;
        }
        Failure<?> failure;
        if (exception instanceof MethodArgumentNotValidException validation) {
            failure = validationFailure(VireonValidationFailures.binding(validation.getBindingResult().getAllErrors()));
        } else if (exception instanceof HandlerMethodValidationException validation) {
            failure = validation.isForReturnValue() ? serverFailure(exception)
                    : validationFailure(VireonValidationFailures.method(validation));
        } else if (exception instanceof MethodValidationException validation) {
            failure = VireonValidationFailures.isRequestMethod(validation)
                    ? validationFailure(VireonValidationFailures.method(validation)) : serverFailure(exception);
        } else if (status.is5xxServerError() && (exception instanceof ServletRequestBindingException
                || !(exception instanceof ErrorResponse))) {
            failure = serverFailure(exception);
        } else {
            VireonErrorCode code = errorCodeFor(status);
            failure = new Failure<>(code, code.message(), null);
        }
        // 不使用 Spring 默认 detail（可能包含拒绝值）；仍经过父类的响应状态保护和最终扩展点。
        return super.handleExceptionInternal(exception, failure, headers,
                HttpStatusCode.valueOf(failure.code().httpStatus()), request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Failure<?> failure = (Failure<?>) body;
        ResponseEntity<? extends VireonApiResponse<?>> response = render(failure, headers);
        return response == null ? null : new ResponseEntity<>(response.getBody(), response.getHeaders(), response.getStatusCode());
    }

    private static ResponseEntity<VireonApiResponse<Void>> internalFailure(Exception exception) {
        return render(serverFailure(exception), HttpHeaders.EMPTY);
    }

    private static Failure<Void> serverFailure(Exception exception) {
        LOGGER.error("Unhandled request exception, traceId={}", VireonRequestTraceId.currentTraceId(), exception);
        VireonErrorCode code = VireonCommonErrorCode.INTERNAL_ERROR;
        return new Failure<>(code, code.message(), null);
    }

    private static ResponseEntity<VireonApiResponse<Void>> failure(VireonErrorCode errorCode) {
        return failure(errorCode, HttpHeaders.EMPTY);
    }

    private static ResponseEntity<VireonApiResponse<Void>> failure(
            VireonErrorCode errorCode, HttpHeaders headers) {
        return VireonErrorResponses.failure(errorCode, errorCode.message(), null, headers);
    }

    private static ResponseEntity<VireonApiResponse<List<VireonValidationError>>> validationResponse(
            List<VireonValidationError> errors) {
        return render(validationFailure(errors), HttpHeaders.EMPTY);
    }

    private static Failure<List<VireonValidationError>> validationFailure(List<VireonValidationError> errors) {
        VireonErrorCode errorCode = VireonCommonErrorCode.VALIDATION_FAILED;
        return new Failure<>(errorCode, errorCode.message(), errors);
    }

    private static <T> ResponseEntity<VireonApiResponse<T>> render(Failure<T> failure, HttpHeaders headers) {
        return VireonErrorResponses.failure(failure.code(), failure.message(), failure.data(), headers);
    }

    private record Failure<T>(VireonErrorCode code, String message, T data) {}

    private static VireonErrorCode errorCodeFor(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> VireonCommonErrorCode.BAD_REQUEST;
            case 401 -> VireonCommonErrorCode.UNAUTHORIZED;
            case 403 -> VireonCommonErrorCode.FORBIDDEN;
            case 404 -> VireonCommonErrorCode.NOT_FOUND;
            case 405 -> VireonCommonErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> VireonCommonErrorCode.CONFLICT;
            case 415 -> VireonCommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> httpStatusErrorCode(statusCode);
        };
    }

    private static VireonErrorCode httpStatusErrorCode(HttpStatusCode statusCode) {
        int status = statusCode.value();
        HttpStatus standardStatus = HttpStatus.resolve(status);
        String message = standardStatus == null ? "HTTP error" : standardStatus.getReasonPhrase();
        return new HttpStatusErrorCode("VIREON.HTTP." + status, message, status);
    }

    private record HttpStatusErrorCode(String code, String message, int httpStatus) implements VireonErrorCode {}
}
