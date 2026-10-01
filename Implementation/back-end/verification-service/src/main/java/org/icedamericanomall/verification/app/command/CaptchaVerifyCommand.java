package org.icedamericanomall.verification.app.command;

/**
 * 人机验证命令（应用层入参）。
 *
 * @param provider      人机验证厂商（GEETEST / ...），为空时用默认厂商
 * @param lotNumber     验证流水号
 * @param captchaOutput 验证输出信息
 * @param passToken     验证通过标识
 * @param genTime       验证通过时间戳（毫秒）
 * @param clientIp      客户端 IP（风控归因）
 */
public record CaptchaVerifyCommand(String provider,
                                   String lotNumber,
                                   String captchaOutput,
                                   String passToken,
                                   String genTime,
                                   String clientIp) {
}
