package org.icedamericanomall.auth.app.dto;

/**
 * 人机验证凭证（应用层入参值对象）—— 前端提交的厂商二次校验四要素。
 *
 * <p>字段与极验 v4「二次校验」接口一一对应；{@code captchaId} 与签名由服务端生成，
 * 故<b>不接受</b>前端传入（前端拿到的 {@code captchaId} 只是初始化组件的公开标识）。
 *
 * <p>为什么用值对象而不是不透明字符串：原实现把 payload 当字符串，需要中间做一次
 * JSON 编解码才能取到四个字段；改为强类型后，校验与映射都是编译期可见的。
 *
 * @param provider      人机验证厂商（GEETEST / ...），为空时由基础设施层取配置默认厂商
 * @param lotNumber     验证流水号
 * @param captchaOutput 验证输出信息
 * @param passToken     验证通过标识
 * @param genTime       验证通过时间戳（毫秒）
 * @param clientIp      客户端 IP（风控归因，由适配层解析 X-Forwarded-For 首段）
 */
public record CaptchaVerifyParam(String provider,
                                 String lotNumber,
                                 String captchaOutput,
                                 String passToken,
                                 String genTime,
                                 String clientIp) {

    /**
     * 是否携带了人机验证参数（四要素任一非空即算携带）。
     *
     * <p>用「任一非空」而非「全部非空」：否则前端只传一个残缺字段就会被当成"未携带"而
     * <b>跳过</b>校验，形成绕过面。残缺参数必须走校验并被拒绝，见 {@link #isComplete()}。
     */
    public boolean isProvided() {
        return hasText(lotNumber) || hasText(captchaOutput) || hasText(passToken) || hasText(genTime);
    }

    /** 四要素是否齐全（不齐则无法通过厂商校验，直接按参数错误拒绝，不打下游） */
    public boolean isComplete() {
        return hasText(lotNumber) && hasText(captchaOutput) && hasText(passToken) && hasText(genTime);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
