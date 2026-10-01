package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.AddressClient;
import org.icedamericanomall.dto.AddressCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AddressClientFallback implements FallbackFactory<AddressClient> {
    @Override
    public AddressClient create(Throwable cause) {
        return new AddressClient() {
            @Override
            public AddressCO getAddress(Long id) {
                log.error("获取地址失败, addressId={}", id, cause);
                return null;
            }
        };
    }
}
