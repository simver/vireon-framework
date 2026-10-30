package com.simxe.vireon.openapi;

import io.swagger.v3.oas.annotations.Hidden;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅显式启用评审时注册的附加资源。 */
@Hidden
@RestController
public final class OpenApiReviewController {
    private final OpenApiMetadata metadata;
    OpenApiReviewController(OpenApiMetadata metadata) { this.metadata = metadata; }
    @GetMapping(value = "${vireon.openapi.path:/docs}/review-plugin.js", produces = "application/javascript")
    public byte[] plugin() throws IOException { return OpenApiController.resource("review-plugin.js"); }
    @GetMapping(value = "${vireon.openapi.path:/docs}/review.json", produces = "application/json")
    public byte[] review() { return metadata.reviewBytes(); }
}
