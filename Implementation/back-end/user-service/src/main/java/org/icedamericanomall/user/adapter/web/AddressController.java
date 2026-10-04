package org.icedamericanomall.user.adapter.web;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.group.CreateGroup;
import org.icedamericanomall.user.adapter.group.UpdateGroup;
import org.icedamericanomall.user.adapter.request.AddressReq;
import org.icedamericanomall.user.adapter.response.AddressResp;
import org.icedamericanomall.user.app.address.AddressManager;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址接口（适配层）—— 只做参数校验 + 委托 Manager + Result 包装；
 * 归属用户一律取自登录上下文（{@link UserContext}），不信任前端传参。
 */
@RestController
@RequestMapping("/api/user/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressManager addressManager;

    /** 当前用户收货地址列表。 */
    @GetMapping("/list")
    public Result<List<AddressResp>> getList() {
        return Result.ok(addressManager.listByUser(UserContext.getUserId()));
    }

    /** 新增收货地址（归属取自登录上下文）。 */
    @PostMapping("/add")
    public Result<Void> addAddress(@RequestBody @Validated(CreateGroup.class) AddressReq req) {
        addressManager.addAddress(UserContext.getUserId(), req);
        return Result.ok();
    }

    /** 查询收货地址详情。 */
    @GetMapping("/{id}")
    public Result<AddressResp> getById(@PathVariable Long id) {
        return Result.ok(addressManager.getById(id));
    }

    /** 修改收货地址（仅限本人）。 */
    @PutMapping("/update/{id}")
    public Result<Void> update(
            @PathVariable Long id,
            @RequestBody @Validated(UpdateGroup.class) AddressReq req) {
        addressManager.updateAddress(UserContext.getUserId(), id, req);
        return Result.ok();
    }

    /** 设为默认收货地址（仅限本人）。 */
    @PutMapping("/default/{id}")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressManager.setDefault(UserContext.getUserId(), id);
        return Result.ok();
    }

    /** 删除收货地址（仅限本人）。 */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressManager.deleteAddress(UserContext.getUserId(), id);
        return Result.ok();
    }
}
