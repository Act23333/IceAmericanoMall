package org.icedamericanomall.user.domain.model;

/**
 * 一次人机验证尝试（领域值对象）—— 极验 Geetest v4 四要素 + 客户端 IP。
 *
 * <p>参数原样透传给 verification-service 做二次校验（ADR-007：人机验证能力已收口），
 * 本服务不做任何本地判定。
 *
 * @param lotNumber     极验 lotNumber
 * @param captchaOutput 极验 captchaOutput
 * @param passToken     极验 passToken
 * @param genTime       极验 genTime
 * @param clientIp      客户端 IP（校验方风控输入）
 */
public record CaptchaAttempt(String lotNumber, String captchaOutput,
                             String passToken, String genTime, String clientIp) {

    /** 四要素是否完整（缺一即视为未携带人机验证参数）。 */
    public boolean isComplete() {
        return isNotBlank(lotNumber) && isNotBlank(captchaOutput)
                && isNotBlank(passToken) && isNotBlank(genTime);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
