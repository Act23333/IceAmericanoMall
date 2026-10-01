package org.icedamericanomall.auth.app.dto;

/**
 * 验证码签发结果（应用层 DTO）。
 *
 * <p>签发失败一律以 {@link org.icedamericanomall.common.exception.BizException} 表达（异常是唯一失败出口），
 * 因此本对象只承载成功时的信息。
 *
 * @param expireSeconds 验证码有效期（秒）
 */
public record IssuedCodeDTO(Integer expireSeconds) {
}
