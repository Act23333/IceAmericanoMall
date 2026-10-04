package org.icedamericanomall.auth.adapter.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.io.Serial;
import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@SuperBuilder
@Jacksonized //试用Builder构造器来反序列化
@ToString(callSuper = true)
public class WechatWebLoginReq extends BaseLoginReq implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "code 不能为空")
    private String code;

    @NotBlank(message = "state 不能为空")
    private String state;
}