package com.xhunting.coreapi.handler;

/**
 * Tương đương {@code sdf.Context} trong proto: grpc-gateway điền {@code ctx.tenant_id}, {@code ctx.id}
 * từ URL, còn header Authorization đi qua gRPC metadata.
 *
 * @param authorization giá trị header "Authorization"
 * @param tenantId      {tenantId} trên URL
 * @param id            {id} trên URL (case id), có thể null
 */
public record RequestContext(String authorization, String tenantId, String id) {
}
