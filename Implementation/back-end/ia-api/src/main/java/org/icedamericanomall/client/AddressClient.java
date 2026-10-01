package org.icedamericanomall.client;

import org.icedamericanomall.dto.AddressCO;
import org.icedamericanomall.fallback.AddressClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for user-service internal address endpoints.
 * Used by trade-service to snapshot address at order time.
 *
 * <p>Server endpoint: {@code GET /internal/address/{id}} in user-service
 * {@code InternalAddressController}（契约路径以此为准 —— 修复客户端曾多拼一层
 * {@code /address} 导致运行期 404 的问题，与服务端路由对齐）。
 */
@FeignClient(name = "user-service", path = "/internal/address", contextId = "address",
        fallbackFactory = AddressClientFallback.class)
public interface AddressClient {

    /**
     * Get address by ID for order address snapshot.
     */
    @GetMapping("/{id}")
    AddressCO getAddress(@PathVariable Long id);
}
