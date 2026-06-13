package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.service.AddressService;
import org.icedAmericanoMall.domain.dto.AddressReq;
import org.icedAmericanoMall.domain.vo.AddressResp;
import org.icedAmericanoMall.group.CreateGroup;
import org.icedAmericanoMall.group.UpdateGroup;
import org.noLazy.common.domain.Result;
import org.noLazy.common.utils.UserContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/user/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/list")
    public Result<List<AddressResp>> getList() {
        Long currentUserId = UserContext.getUser();
        return Result.ok(addressService.getListByCurrentUser(currentUserId));
    }

    @PostMapping("/add")
    public Result<Void> addAddress(@RequestBody @Validated(CreateGroup.class) AddressReq req) {
        addressService.addAddress(req);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<AddressResp> getById(@PathVariable Long id) {
        return Result.ok(addressService.getAddressById(id));
    }

    @PutMapping("/update/{id}")
    public Result<Void> update(
            @PathVariable Long id,
            @RequestBody @Validated(UpdateGroup.class) AddressReq req) {
        addressService.updateAddress(id, req);
        return Result.ok();
    }

    @PutMapping("/default/{id}")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(id);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.deleteAddress(id);
        return Result.ok();
    }
}
