package com.simxe.vireon.web.api;

import com.simxe.vireon.web.context.VireonRequestTraceId;

/** Web 层显式成功响应工厂；不会从原始请求头建立追踪上下文。 */
public final class VireonResponses {

    private VireonResponses() {}

    public static <T> VireonApiResponse<T> ok(T data) {
        return VireonApiResponse.success(data, VireonRequestTraceId.currentTraceId());
    }

    public static VireonApiResponse<Void> ok() {
        return VireonApiResponse.success(VireonRequestTraceId.currentTraceId());
    }
}
