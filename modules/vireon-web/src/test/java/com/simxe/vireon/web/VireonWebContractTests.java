package com.simxe.vireon.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.simxe.vireon.core.error.VireonCommonErrorCode;
import com.simxe.vireon.core.error.VireonException;
import com.simxe.vireon.core.http.VireonHeaders;
import com.simxe.vireon.core.http.VireonRequestAttributes;
import com.simxe.vireon.web.api.VireonApiResponse;
import com.simxe.vireon.web.api.VireonResponses;
import com.simxe.vireon.web.error.VireonGlobalExceptionHandler;
import com.simxe.vireon.web.error.VireonValidationError;
import com.simxe.vireon.web.response.VireonResponseProtocolAdvice;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ExtendWith(OutputCaptureExtension.class)
class VireonWebContractTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ContractController())
                .setControllerAdvice(
                        new VireonResponseProtocolAdvice(), new VireonGlobalExceptionHandler())
                .build();
    }

    @Test
    void writesExplicitResponseWithTraceId() throws Exception {
        mockMvc.perform(get("/contract/value")
                        .requestAttr(VireonRequestAttributes.TRACE_ID, "request-trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(VireonApiResponse.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.value").value("ready"))
                .andExpect(jsonPath("$.traceId").value("request-trace"));
    }

    @Test
    void doesNotTrustUnvalidatedTraceHeaderWithoutFilter() throws Exception {
        mockMvc.perform(get("/contract/value").header(VireonHeaders.TRACE_ID, "bad trace value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(nullValue()));
    }

    @Test
    void doesNotWrapExistingResponse() throws Exception {
        mockMvc.perform(get("/contract/wrapped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(VireonApiResponse.SUCCESS_CODE))
                .andExpect(jsonPath("$.data").value("ready"));
    }

    @Test
    void serializesStringResponseAsJsonEnvelope() throws Exception {
        mockMvc.perform(get("/contract/wrapped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(VireonApiResponse.SUCCESS_CODE))
                .andExpect(jsonPath("$.data").value("ready"));
    }

    @Test
    void keepsExplicitlyAcceptedTextResponseUnwrapped() throws Exception {
        mockMvc.perform(get("/contract/text").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("ready"));
    }

    @Test
    void keepsExplicitTextRepresentationUnwrapped() throws Exception {
        mockMvc.perform(get("/contract/explicit-text"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("ready"));
    }

    @Test
    void serializesNullStringResponseAsJsonEnvelope() throws Exception {
        mockMvc.perform(get("/contract/null-data"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(VireonApiResponse.SUCCESS_CODE))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void leavesBodiesHandledByNonEnvelopeCompatibleConvertersUntouched() throws Exception {
        mockMvc.perform(get("/contract/form"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("state=ready"));
    }

    @Test
    void keepsExplicitTextContentTypeAndLengthWithoutWrapping() throws Exception {
        mockMvc.perform(get("/contract/fixed-length"))
                .andExpect(status().isOk())
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 5))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("ready"));
    }

    @Test
    void doesNotImplicitlyWrapRawDtoOrNullString() throws Exception {
        mockMvc.perform(get("/contract/raw"))
                .andExpect(content().json("{\"value\":\"ready\"}"))
                .andExpect(jsonPath("$.code").doesNotExist());
        mockMvc.perform(get("/contract/null-text"))
                .andExpect(status().isOk()).andExpect(content().string(""));
    }

    @Test
    void keepsNoContentResponseBodyEmpty() throws Exception {
        mockMvc.perform(get("/contract/no-content"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void keepsNoContentResponseBodyEmptyForNonJacksonConverter() throws Exception {
        mockMvc.perform(get("/contract/form-no-content"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void keepsResetContentResponseBodyEmpty() throws Exception {
        mockMvc.perform(get("/contract/reset-content"))
                .andExpect(status().isResetContent())
                .andExpect(content().string(""));
    }

    @Test
    void keepsNotModifiedResponseBodyEmpty() throws Exception {
        mockMvc.perform(get("/contract/not-modified"))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }

    @Test
    void keepsHeadResponseBodyEmpty() throws Exception {
        mockMvc.perform(head("/contract/value"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void doesNotRelabelExplicitErrorResponseAsSuccess() throws Exception {
        mockMvc.perform(get("/contract/explicit-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("missing"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void keepsSingleResourceRegionOutsideBusinessEnvelope() throws Exception {
        mockMvc.perform(get("/contract/resource-region"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 1-3/5"))
                .andExpect(content().bytes("ead".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void keepsMultipleResourceRegionsOutsideBusinessEnvelope() throws Exception {
        String response = mockMvc.perform(get("/contract/resource-regions"))
                .andExpect(status().isPartialContent())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(response).contains("ab").contains("ef").doesNotContain(VireonApiResponse.SUCCESS_CODE);
    }

    @Test
    void returnsStructuredValidationErrors() throws Exception {
        mockMvc.perform(post("/contract/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data[0].path").value("name"))
                .andExpect(jsonPath("$.data[0].code").value("VIREON.VALIDATION.NOT_BLANK"));
    }

    @Test
    void retainsClassLevelValidationErrors() throws Exception {
        mockMvc.perform(post("/contract/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data[0].path").value("contractRequest"))
                .andExpect(jsonPath("$.data[0].code").value("VIREON.VALIDATION.VALID_CONTRACT_REQUEST"));
    }

    @Test
    void mapsHandlerMethodValidationToBadRequest() throws Exception {
        mockMvc.perform(get("/contract/positive").param("value", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data[0].path").value("value"))
                .andExpect(jsonPath("$.data[0].code").value("VIREON.VALIDATION.MIN"));
    }

    @Test
    void mapsInvalidControllerReturnValueToServerErrorAndLogsEvidence(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/contract/invalid-return")
                        .requestAttr(VireonRequestAttributes.TRACE_ID, "return-trace"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.INTERNAL_ERROR.code()));

        assertThat(output)
                .contains("Unhandled request exception, traceId=return-trace")
                .contains("HandlerMethodValidationException");
    }

    @Test
    void mapsUnsupportedMethodToMethodNotAllowed() throws Exception {
        mockMvc.perform(post("/contract/value"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, HttpMethod.GET.name()))
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.METHOD_NOT_ALLOWED.code()));
    }

    @Test
    void mapsUnsupportedMediaTypeToClientError() throws Exception {
        mockMvc.perform(post("/contract/validated")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=ready"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.ACCEPT, "application/json, application/yaml, application/*+json"))
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.UNSUPPORTED_MEDIA_TYPE.code()));
    }

    @Test
    void mapsMissingRequestHeaderToBadRequest() throws Exception {
        mockMvc.perform(get("/contract/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.BAD_REQUEST.code()));
    }

    @Test
    void mapsMissingRequestCookieToBadRequest() throws Exception {
        mockMvc.perform(get("/contract/cookie"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.BAD_REQUEST.code()));

        mockMvc.perform(get("/contract/cookie").cookie(new Cookie("session", "ready")))
                .andExpect(status().isOk());
    }

    @Test
    void mapsMissingRequestPartToBadRequest() throws Exception {
        mockMvc.perform(multipart("/contract/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.BAD_REQUEST.code()));
    }

    @Test
    void keepsMissingPathVariableAsServerErrorAndLogsEvidence(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/contract/path/1")
                        .requestAttr(VireonRequestAttributes.TRACE_ID, "binding-trace"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.INTERNAL_ERROR.code()));

        assertThat(output)
                .contains("Unhandled request exception, traceId=binding-trace")
                .contains("MissingPathVariableException");
    }

    @Test
    void mapsMissingResourceToNotFound() throws Exception {
        mockMvc.perform(get("/contract/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.NOT_FOUND.code()));
    }

    @Test
    void mapsBusinessErrorToDeclaredStatus() throws Exception {
        mockMvc.perform(get("/contract/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.CONFLICT.code()))
                .andExpect(jsonPath("$.message").value("State changed"));
    }

    @Test
    void preservesResponseStatusException() throws Exception {
        mockMvc.perform(get("/contract/status-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.NOT_FOUND.code()))
                .andExpect(jsonPath("$.message").value(VireonCommonErrorCode.NOT_FOUND.message()));
    }

    @Test
    void preservesUnmappedClientStatusWithoutMislabelingItAsBadRequest() throws Exception {
        mockMvc.perform(get("/contract/too-many-requests"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VIREON.HTTP.429"))
                .andExpect(jsonPath("$.message").value("Too Many Requests"));
    }

    @Test
    void preservesUnmappedServerStatusWithoutMislabelingItAsInternalError() throws Exception {
        mockMvc.perform(get("/contract/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("VIREON.HTTP.503"))
                .andExpect(jsonPath("$.message").value("Service Unavailable"));
    }

    @Test
    void preservesErrorResponseImplementationsOutsideErrorResponseExceptionHierarchy() throws Exception {
        mockMvc.perform(get("/contract/upload-too-large"))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("VIREON.HTTP.413"));

        mockMvc.perform(get("/contract/async-timeout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("VIREON.HTTP.503"));
    }

    @Test
    void preservesNotAcceptableWithoutTryingToWriteAnUnacceptableEnvelope() throws Exception {
        mockMvc.perform(get("/contract/value").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().string(""));
    }

    @Test
    void returnsNotAcceptableWhenBusinessErrorEnvelopeIsNotAccepted() throws Exception {
        mockMvc.perform(get("/contract/conflict").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(header().string(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().string(""));
    }

    @Test
    void returnsNotAcceptableWhenValidationErrorEnvelopeIsNotAccepted() throws Exception {
        mockMvc.perform(post("/contract/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isNotAcceptable())
                .andExpect(header().string(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().string(""));
    }

    @Test
    void publicResponseRecordsProvideChineseOpenApiDescriptions() {
        assertRecordComponentsHaveChineseSchemaDescriptions(VireonApiResponse.class);
        assertRecordComponentsHaveChineseSchemaDescriptions(VireonValidationError.class);
    }

    @Test
    void hidesUnexpectedExceptionDetailsAndLogsServerEvidence(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/contract/failure")
                        .requestAttr(VireonRequestAttributes.TRACE_ID, "failure-trace"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.INTERNAL_ERROR.code()))
                .andExpect(jsonPath("$.message").value(VireonCommonErrorCode.INTERNAL_ERROR.message()));

        assertThat(output)
                .contains("Unhandled request exception, traceId=failure-trace")
                .contains("must not escape");
    }

    @Test
    void hidesMalformedRequestDetails() throws Exception {
        mockMvc.perform(post("/contract/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(VireonCommonErrorCode.BAD_REQUEST.code()))
                .andExpect(jsonPath("$.message").value(VireonCommonErrorCode.BAD_REQUEST.message()));
    }

    @RestController
    static class ContractController {

        @GetMapping("/contract/value")
        VireonApiResponse<Map<String, String>> value() {
            return VireonResponses.ok(Map.of("value", "ready"));
        }

        @GetMapping("/contract/raw")
        Map<String, String> raw() {
            return Map.of("value", "ready");
        }

        @GetMapping("/contract/null-data")
        VireonApiResponse<String> nullData() {
            return VireonResponses.ok(null);
        }

        @GetMapping("/contract/wrapped")
        VireonApiResponse<String> wrapped() {
            return VireonApiResponse.success("ready", null);
        }

        @GetMapping("/contract/text")
        String text() {
            return "ready";
        }

        @GetMapping(value = "/contract/explicit-text", produces = MediaType.TEXT_PLAIN_VALUE)
        String explicitText() {
            return "ready";
        }

        @GetMapping("/contract/null-text")
        String nullText() {
            return null;
        }

        @GetMapping(value = "/contract/form", produces = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        MultiValueMap<String, String> form() {
            LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("state", "ready");
            return body;
        }

        @GetMapping("/contract/fixed-length")
        ResponseEntity<String> fixedLength() {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN).contentLength(5).body("ready");
        }

        @GetMapping("/contract/no-content")
        ResponseEntity<Map<String, String>> noContent() {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(Map.of("must", "not be written"));
        }

        @GetMapping(value = "/contract/form-no-content", produces = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        ResponseEntity<MultiValueMap<String, String>> formNoContent() {
            LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("must", "not be written");
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(body);
        }

        @GetMapping("/contract/reset-content")
        ResponseEntity<Map<String, String>> resetContent() {
            return ResponseEntity.status(HttpStatus.RESET_CONTENT).body(Map.of("must", "not be written"));
        }

        @GetMapping("/contract/not-modified")
        ResponseEntity<Map<String, String>> notModified() {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).body(Map.of("must", "not be written"));
        }

        @GetMapping("/contract/explicit-error")
        ResponseEntity<Map<String, String>> explicitError() {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("reason", "missing"));
        }

        @GetMapping(value = "/contract/resource-region", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
        ResponseEntity<ResourceRegion> resourceRegion() {
            ByteArrayResource resource = new ByteArrayResource("ready".getBytes(StandardCharsets.UTF_8));
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new ResourceRegion(resource, 1, 3));
        }

        @GetMapping(value = "/contract/resource-regions", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
        ResponseEntity<List<ResourceRegion>> resourceRegions() {
            ByteArrayResource resource = new ByteArrayResource("abcdef".getBytes(StandardCharsets.UTF_8));
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(List.of(new ResourceRegion(resource, 0, 2), new ResourceRegion(resource, 4, 2)));
        }

        @PostMapping("/contract/validated")
        VireonApiResponse<ContractRequest> validated(@Valid @RequestBody ContractRequest request) {
            return VireonResponses.ok(request);
        }

        @GetMapping("/contract/positive")
        int positive(@RequestParam @Min(1) int value) {
            return value;
        }

        @GetMapping("/contract/invalid-return")
        @Min(1)
        int invalidReturn() {
            return 0;
        }

        @GetMapping("/contract/header")
        String header(@RequestHeader("X-Required") String value) {
            return value;
        }

        @GetMapping("/contract/cookie")
        String cookie(@CookieValue("session") String value) {
            return value;
        }

        @PostMapping(path = "/contract/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        String upload(@RequestPart("file") MultipartFile file) {
            return file.getOriginalFilename();
        }

        @GetMapping("/contract/path/{id}")
        String path(@PathVariable("missing") String value) {
            return value;
        }

        @GetMapping("/contract/missing-resource")
        void missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/contract/missing-resource");
        }

        @GetMapping("/contract/conflict")
        void conflict() {
            throw new VireonException(VireonCommonErrorCode.CONFLICT, "State changed");
        }

        @GetMapping("/contract/status-error")
        void statusError() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "must not escape");
        }

        @GetMapping("/contract/too-many-requests")
        void tooManyRequests() {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "must not escape");
        }

        @GetMapping("/contract/unavailable")
        void unavailable() {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "must not escape");
        }

        @GetMapping("/contract/upload-too-large")
        void uploadTooLarge() {
            throw new MaxUploadSizeExceededException(1);
        }

        @GetMapping("/contract/async-timeout")
        void asyncTimeout() {
            throw new AsyncRequestTimeoutException();
        }

        @GetMapping("/contract/failure")
        void failure() {
            throw new IllegalStateException("must not escape");
        }
    }

    private static void assertRecordComponentsHaveChineseSchemaDescriptions(Class<?> recordType) {
        assertThat(recordType.getRecordComponents()).allSatisfy(component -> {
            Schema schema = component.getAccessor().getAnnotation(Schema.class);
            assertThat(schema).as(component.getName()).isNotNull();
            assertThat(schema.description()).as(component.getName()).containsPattern("[\\p{IsHan}]");
        });
    }

    @ValidContractRequest
    record ContractRequest(@NotBlank String name) {}

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = ContractRequestValidator.class)
    @interface ValidContractRequest {

        String message() default "Contract request is invalid";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static final class ContractRequestValidator
            implements ConstraintValidator<ValidContractRequest, ContractRequest> {

        @Override
        public boolean isValid(ContractRequest value, ConstraintValidatorContext context) {
            return value == null || !"invalid".equals(value.name());
        }
    }
}
