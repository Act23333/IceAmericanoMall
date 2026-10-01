package org.icedamericanomall.user.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.AddressResp;
import org.icedamericanomall.user.app.address.AddressManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 地址域内部接口 —— Feign 调用（trade-service 下单地址快照，{@code AddressClient} 对端）。
 *
 * <p>契约路径 {@code GET /internal/address/{id}} 与报文（AddressResp 字段超集）保持不变；
 * {@code /internal/**} 裸异常语义（不包装 Result）。
 */
@RestController
@RequestMapping("/internal/address")
@RequiredArgsConstructor
public class InternalAddressController {

    private final AddressManager addressManager;

    @GetMapping("/{id}")
    public AddressResp getAddress(@PathVariable Long id) {
        return addressManager.getById(id);
    }
}
