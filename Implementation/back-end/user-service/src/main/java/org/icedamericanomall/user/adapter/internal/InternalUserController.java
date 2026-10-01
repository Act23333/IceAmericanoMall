package org.icedamericanomall.user.adapter.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.vo.UserInfoResp;
import org.icedamericanomall.dto.UserAuthCO;
import org.icedamericanomall.user.service.UserService;
import org.springframework.web.bind.annotation.*;

/**
 * 用户域内部接口 —— 仅含纯用户查询（auth/address/points/balance 已拆至各自领域 InternalController）。
 */
@RestController
@RequestMapping("/internal/user")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public UserInfoResp getUser(@PathVariable Long id) {
        return userService.getByUserId(id);
    }

    @GetMapping("/count")
    public long countUsers() {
        return userService.countUsers();
    }

    @PostMapping("/getOrCreate")
    public Long getOrCreateUser(@RequestBody UserAuthCO userAuthDTO) {

        return userService.getOrCreateUser(userAuthDTO);
    }
}
