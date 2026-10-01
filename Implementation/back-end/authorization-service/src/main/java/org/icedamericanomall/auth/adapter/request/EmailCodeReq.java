package org.icedamericanomall.auth.adapter.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 邮箱验证码签发请求（公开端点 {@code POST /api/auth/email/code}）。
 *
 * <p>校验分两层：适配层用 Bean Validation 快速失败（{@code @NotBlank/@Email/@Size}），
 * 应用层再用领域值对象 {@code EmailAddress} 归一化并严格校验
 * （大小写归一 + 更严格的域名/顶级域规则，保证与盲索引、Redis Key 三方一致）。
 *
 * <p>重发限流（60 秒 / 5 次）由 verification-service 内建，本服务不重复实现。
 */
@Getter
@Setter
public class EmailCodeReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 收件邮箱 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱长度不能超过 254")
    private String email;

    /**
     * 业务场景（可选，缺省 LOGIN）。
     *
     * <p>用字符串而非枚举接收：枚举解析失败会在 Jackson 反序列化阶段抛出并被包装为
     * {@code HttpMessageNotReadableException}（最终 500），而由适配层显式解析可以返回
     * 明确的 {@code 1101 参数错误}。取值：{@code LOGIN} / {@code REGISTER} / {@code RESET_PASSWORD}。
     */
    private String scene;
}
