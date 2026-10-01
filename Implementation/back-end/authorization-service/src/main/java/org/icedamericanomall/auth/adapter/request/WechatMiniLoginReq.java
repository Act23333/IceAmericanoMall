package org.icedamericanomall.auth.adapter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import java.io.Serializable;

/**
 * @ClassName: WechatMiniLoginReq
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/9/13 01:28
 * @Version: 1.0.0
 * @ProjectName: back-end
 * @Package: org.icedamericanomall.auth.domain.auth.login.request
 */
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@SuperBuilder
@Jacksonized //试用Builder构造器来反序列化
@ToString(callSuper = true)
public class WechatMiniLoginReq extends BaseLoginReq implements Serializable {
    @NotBlank(message = "code 不能为空")
    private String code;
    private String phoneCode;
//    private String nickname;
//    private String avatarUrl;
}
