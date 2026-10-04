package org.icedamericanomall.verification.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.integration.captcha.CaptchaClient;
import org.icedamericanomall.integration.captcha.Geetest4ValidateRequest;
import org.icedamericanomall.verification.app.command.CaptchaVerifyCommand;
import org.icedamericanomall.verification.app.port.CaptchaVerifyService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 人机验证端口实现（基础设施层）—— 按 provider 路由到 ia-integration 的验证厂商客户端。
 *
 * <p><b>厂商解包的唯一位置</b>：命令与契约（ia-api CO）都是厂商中立参数集，
 * 厂商专属字段只在本类落地。新增厂商 = 新增一个分支 + 对应的 ia-integration 适配器。
 * 参数残缺或未知 provider 一律返回 {@code false}（安全优先：拒绝而非放行）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaptchaVerifyServiceImpl implements CaptchaVerifyService {

    private static final String PROVIDER_GEETEST = "GEETEST";

    /** 极验 v4 二次校验四要素的契约 key（前端与 ia-api CO 的 params 约定） */
    private static final String KEY_LOT_NUMBER = "lotNumber";
    private static final String KEY_CAPTCHA_OUTPUT = "captchaOutput";
    private static final String KEY_PASS_TOKEN = "passToken";
    private static final String KEY_GEN_TIME = "genTime";

    private final CaptchaClient<Geetest4ValidateRequest> geetestCaptchaClient;

    @Override
    public boolean verify(String provider, CaptchaVerifyCommand command) {
        if (PROVIDER_GEETEST.equalsIgnoreCase(provider)) {
            Map<String, String> params = command.params();
            String lotNumber = params.get(KEY_LOT_NUMBER);
            String captchaOutput = params.get(KEY_CAPTCHA_OUTPUT);
            String passToken = params.get(KEY_PASS_TOKEN);
            String genTime = params.get(KEY_GEN_TIME);
            if (isBlank(lotNumber) || isBlank(captchaOutput) || isBlank(passToken) || isBlank(genTime)) {
                log.warn("[verify][captcha][reject] GEETEST 参数残缺（四要素缺失按校验失败处理，fail-closed）");
                return false;
            }
            Geetest4ValidateRequest request = Geetest4ValidateRequest.builder()
                    .lotNumber(lotNumber)
                    .captchaOutput(captchaOutput)
                    .passToken(passToken)
                    .genTime(genTime)
                    .build();
            return geetestCaptchaClient.verify(request);
        }
        log.warn("不支持的人机验证厂商: {}", provider);
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
