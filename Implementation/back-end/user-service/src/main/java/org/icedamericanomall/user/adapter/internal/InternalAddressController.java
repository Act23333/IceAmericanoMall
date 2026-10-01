package org.icedamericanomall.user.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.vo.AddressResp;
import org.icedamericanomall.user.service.AddressService;
import org.springframework.web.bind.annotation.*;

/**
 * 地址域内部接口 —— Feign 调用（从原 InternalUserController 拆出）。
 */
@RestController
@RequestMapping("/internal/address")
@RequiredArgsConstructor
public class InternalAddressController {

    private final AddressService addressService;

    @GetMapping("/{id}")
    public AddressResp getAddress(@PathVariable Long id) {
        return addressService.getAddressById(id);
    }
}
