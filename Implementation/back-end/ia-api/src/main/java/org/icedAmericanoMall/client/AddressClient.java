package org.icedAmericanoMall.client;

import org.icedAmericanoMall.dto.AddressDTO;
import org.icedAmericanoMall.fallback.AddressClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for user-service internal address endpoints.
 * Used by trade-service to snapshot address at order time.
 *
 * <p>Server endpoint: {@code GET /internal/user/address/{id}} in InternalUserController.
 */
@FeignClient(name = "user-service", path = "/internal/address", contextId = "address",
        fallbackFactory = AddressClientFallback.class)
public interface AddressClient {

    /**
     * Get address by ID for order address snapshot.
     */
    @GetMapping("/address/{id}")
    AddressDTO getAddress(@PathVariable Long id);
}
