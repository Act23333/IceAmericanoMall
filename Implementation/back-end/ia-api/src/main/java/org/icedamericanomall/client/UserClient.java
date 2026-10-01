package org.icedamericanomall.client;

import org.icedamericanomall.dto.UserAuthCO;
import org.icedamericanomall.fallback.UserClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 纯用户域 Feign Client（V2.5 拆分：认证域 / 积分域 / 余额域各自独立契约）。
 *
 * <p>契约修正（V5.2）：{@code getOrCreateUser} 补充 {@code @RequestBody}。
 * 服务端 {@code InternalUserController#getOrCreateUser} 声明的是
 * {@code @RequestBody UserAuthCO}，客户端此前漏标注解 → 请求体不会被序列化发送，
 * 与「wechatMiniLogin 漏 {@code @RequestBody}」属同一类契约缺陷。
 *
 * <p><b>V5.2 边界收敛</b>：认证域 Feign 契约（{@code /internal/auth/**}）已随
 * "凭证唯一所有权归 authorization-service" 整体废弃（ADR-017）—— 本 Client 是全仓
 * 唯一保留的用户域 Feign 契约（count + getOrCreate 幂等建档）。
 */
@FeignClient(name = "user-service", path = "/internal/user", contextId = "user", fallbackFactory = UserClientFallback.class)
public interface UserClient {

    @GetMapping("/count")
    Long countUsers();

    @PostMapping("/getOrCreate")
    Long getOrCreateUser(@RequestBody UserAuthCO userAuthDTO);
}
