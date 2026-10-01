package org.icedamericanomall.user.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.UserInfoResp;
import org.icedamericanomall.dto.UserAuthCO;
import org.icedamericanomall.user.app.user.UserManager;
import org.springframework.web.bind.annotation.*;

/**
 * 用户域内部接口 —— 仅含纯用户查询（auth/address/points/balance 已拆至各自领域 InternalController）。
 */
@RestController
@RequestMapping("/internal/user")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserManager userManager;

    @GetMapping("/{id}")
    public UserInfoResp getUser(@PathVariable Long id) {
        return userManager.getByUserId(id);
    }

    @GetMapping("/count")
    public long countUsers() {
        return userManager.countUsers();
    }

    @PostMapping("/getOrCreate")
    public Long getOrCreateUser(@RequestBody UserAuthCO userAuthDTO) {

        return userManager.getOrCreateUser(userAuthDTO);
    }
}
