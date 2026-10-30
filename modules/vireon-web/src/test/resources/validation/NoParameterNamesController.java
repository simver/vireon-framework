package com.simxe.vireon.web.fixture;

import com.simxe.vireon.web.api.VireonApiResponse;
import com.simxe.vireon.web.api.VireonResponses;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 由回归测试在临时目录编译，故意不启用 -parameters。 */
@RestController
public class NoParameterNamesController {

    @GetMapping("/no-names/count")
    public VireonApiResponse<Integer> count(@RequestParam("external-count") @Min(1) int count) {
        return VireonResponses.ok(count);
    }

    @Validated
    public static class Aop extends NoParameterNamesController {}
}
