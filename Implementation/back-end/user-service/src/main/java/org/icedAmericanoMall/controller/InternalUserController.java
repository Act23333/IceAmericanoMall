package org.icedAmericanoMall.controller;

import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.vo.UserInfoResp;
import org.icedAmericanoMall.service.UserService;
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
}
