package org.icedamericanomall.auth.app.command;

import lombok.Builder;
import lombok.Singular;
import org.icedamericanomall.auth.app.dto.CaptchaVerifyParam;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;
import org.icedamericanomall.auth.domain.model.LoginSpec;
import org.icedamericanomall.common.enums.IdentityTypeEnum;

import java.util.Collections;
import java.util.Map;

/**
 * 登录命令 —— 应用层统一入参（写操作）。
 *
 * <p>各端 Controller 将各自的 Request 转换为此 Command，AuthManager 只接收 Command，
 * 不感知端差异。跨层传递给领域层时通过 {@link #toSpec()} 转成领域入参
 * {@link LoginSpec}，避免领域层反向依赖应用层。
 *
 * <p>使用 record 保证不可变，避免方法修改入参。
 */
@Builder
public record LoginCommand(
        /* 身份类型（用户名/手机号/邮箱/微信 openid） */
        IdentityTypeEnum identityType,

        /* 身份值（具体的用户名、手机号等） */
        String identifier,

        /* 凭证类型（密码/短信/微信 code 等） */
        CredentialTypeEnum credentialType,

        /* 凭证值（密码明文、短信验证码、微信 code 等） */
        String credential,

        /* 平台类型（服务端识别） */
        PlatformEnum platform,

        /* 设备唯一标识（客户端传，需校验） */
        String deviceId,

        // ========== 特定登录方式特有字段（Map） ==========
        @Singular("extra")
        Map<String, Object> extraParams,

        // 服务端注入
        String clientIp,
        String userAgent,

        // 人机验证凭证（前端提交的极验 v4 四要素；未携带时为 null，见 R3 闭环）
        CaptchaVerifyParam captchaVerify
) {

    /** 紧凑构造器：防御性拷贝 */
    public LoginCommand {
        // Map.copyOf 是浅拷贝，并且如果 map 为空，直接返回一个不可修改的空 map 实例
        extraParams = extraParams == null
                ? Collections.emptyMap()
                : Map.copyOf(extraParams);
    }

    /** 从扩展字段取值 */
    public String extra(String key) {
        Object value = extraParams.get(key);
        return value == null ? null : value.toString();
    }

    /** 从扩展字段取值（带默认值） */
    public String extraOrDefault(String key, String defaultValue) {
        String value = extra(key);
        return value == null ? defaultValue : value;
    }

    /** 转换为领域入参（领域层只依赖 LoginSpec） */
    public LoginSpec toSpec() {
        return new LoginSpec(identityType, identifier, credentialType, credential,
                platform, deviceId, clientIp, userAgent, extraParams);
    }
}
