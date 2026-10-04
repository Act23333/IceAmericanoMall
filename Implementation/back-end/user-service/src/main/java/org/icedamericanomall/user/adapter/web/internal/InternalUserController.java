package org.icedamericanomall.user.adapter.web.internal;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.adapter.response.UserInfoResp;
import org.icedamericanomall.dto.UserAuthCO;
import org.icedamericanomall.user.app.user.UserProvisionManager;
import org.icedamericanomall.user.app.user.UserQueryManager;
import org.springframework.web.bind.annotation.*;

/**
 * 用户域内部接口 —— 仅含纯用户查询（auth/address/points/balance 已拆至各自领域 InternalController）。
 *
 * <p>用例族分工（ADR-025）：读取经 {@link UserQueryManager}，幂等开通经 {@link UserProvisionManager}。
 */
@RestController
@RequestMapping("/internal/user")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserQueryManager userQueryManager;
    private final UserProvisionManager userProvisionManager;

    /** 按用户 ID 查询用户信息（Feign 对端 {@code UserClient}）。 */
    @GetMapping("/{id}")
    public UserInfoResp getUser(@PathVariable Long id) {
        return userQueryManager.getByUserId(id);
    }

    /** 用户总数（内部统计）。 */
    @GetMapping("/count")
    public long countUsers() {
        return userQueryManager.countUsers();
    }

    /** 按登录标识幂等查建用户，返回技术主键（ADR-006；供授权域注册/登录后回查）。 */
    @PostMapping("/getOrCreate")
    public Long getOrCreateUser(@RequestBody UserAuthCO userAuthDTO) {
        return userProvisionManager.getOrCreateUser(userAuthDTO);
    }
}
