package org.icedAmericanoMall.client;

import org.icedAmericanoMall.dto.AddressDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for user-service internal address endpoints.
 * Used by trade-service to snapshot address at order time.
 */
@FeignClient(name = "user-service", path = "/user/address", contextId = "address")
public interface AddressClient {

    /**
     * Get address by ID for order address snapshot.
     */
    @GetMapping("/address/{id}")
    AddressDTO getAddress(@PathVariable Long id);
}
