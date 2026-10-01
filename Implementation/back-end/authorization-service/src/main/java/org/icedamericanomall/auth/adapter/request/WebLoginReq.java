package org.icedamericanomall.auth.adapter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.icedamericanomall.auth.domain.enums.CredentialTypeEnum;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.auth.domain.enums.PlatformEnum;

import java.io.Serial;
import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@SuperBuilder
@Jacksonized //试用Builder构造器来反序列化
@ToString(callSuper = true)
public class WebLoginReq extends BaseLoginReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 身份类型（PHONE / USERNAME / EMAIL，策略按"身份 + 凭证"组合分发）
    private IdentityTypeEnum identityType;
    // 身份值
    @NotBlank(message = "account不能为空")
    private String account;
    /**
     * 凭证类型（可选，缺省 PASSWORD）。
     * <p>服务端按它选择登录策略：PASSWORD（密码）/ SMS_CODE（手机号+短信码）/ EMAIL_CODE（邮箱+邮箱码）；
     * 其余取值（微信类等）由专属端点承担，本端点拒绝。
     */
    private CredentialTypeEnum credentialType;
    // 凭证值
    @NotBlank(message = "credential不能为空")
    private String credential;
    // deviceType	设备类型	粗（PC、iOS、Android、小程序）	服务端识别
    private PlatformEnum platformEnum;
    // deviceId	设备唯一标识	细（每台设备一个）	客户端生成 + 持久化
    private String deviceId;
}
