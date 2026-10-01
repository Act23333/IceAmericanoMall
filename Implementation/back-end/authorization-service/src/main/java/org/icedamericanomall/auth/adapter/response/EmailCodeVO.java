package org.icedamericanomall.auth.adapter.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.auth.app.dto.IssuedCodeDTO;

/**
 * 邮箱验证码签发响应（适配层 VO）。
 *
 * <p>只回"已受理 + 多久有效 + 发到哪个脱敏邮箱"，<b>不回验证码明文</b>（只经邮件通道投递）。
 */
@Data
@Builder
public class EmailCodeVO {

    /** 提示信息（前端可直接展示） */
    private String message;

    /** 验证码有效期（秒） */
    @JsonProperty("expire_seconds")
    private Integer expireSeconds;

    /** 收件邮箱（脱敏，如 {@code ab***@example.com}） */
    private String email;

    /**
     * 应用层结果 → 适配层 VO。
     *
     * @param issued       签发结果
     * @param maskedEmail  脱敏后的收件邮箱
     * @return 响应 VO
     */
    public static EmailCodeVO from(IssuedCodeDTO issued, String maskedEmail) {
        return EmailCodeVO.builder()
                .message("验证码已发送，请注意查收")
                .expireSeconds(issued.expireSeconds())
                .email(maskedEmail)
                .build();
    }
}
