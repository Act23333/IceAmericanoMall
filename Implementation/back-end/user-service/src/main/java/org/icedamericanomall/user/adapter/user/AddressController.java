package org.icedamericanomall.user.adapter.user;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.service.AddressService;
import org.icedamericanomall.user.domain.dto.AddressReq;
import org.icedamericanomall.user.domain.vo.AddressResp;
import org.icedamericanomall.user.group.CreateGroup;
import org.icedamericanomall.user.group.UpdateGroup;
import org.icedamericanomall.common.domain.Result;
import org.icedamericanomall.common.domain.UserContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/user/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/list")
    public Result<List<AddressResp>> getList() {
        Long currentUserId = UserContext.getUserId();
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
