package org.icedamericanomall.integration.captcha;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.utils.HttpUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;

/**
 * 极验 v4（Geetest 4）人机验证客户端。
 *
 * <p>流程：表单参数（lot_number / captcha_output / pass_token / gen_time / captcha_id）
 * → {@link Geetest4SignUtil} 生成 sign_token（HMAC-SHA256）→ POST 极验校验地址 → 判定 result。
 *
 * <p>网络异常与参数为空一律返回 {@code false}（安全优先：验证失败即拒绝），
 * 不做异常上抛，避免把第三方抖动放大为业务异常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeetestCaptchaClient implements CaptchaClient<Geetest4ValidateRequest> {

    public static final String SUCCESS = "success";

    private static final String PARAM_LOT_NUMBER = "lot_number";
    private static final String PARAM_CAPTCHA_OUTPUT = "captcha_output";
    private static final String PARAM_PASS_TOKEN = "pass_token";
    private static final String PARAM_GEN_TIME = "gen_time";
    private static final String PARAM_CAPTCHA_ID = "captcha_id";
    private static final String PARAM_SIGN_TOKEN = "sign_token";

    private final GeetestProperties properties;

    @Override
    public boolean verify(Geetest4ValidateRequest request) {
        if (request == null) {
            log.warn("极验验证请求参数为空");
            return false;
        }
        return doValidate(request);
    }

    private boolean doValidate(Geetest4ValidateRequest request) {
        // 1. 构建表单参数
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add(PARAM_LOT_NUMBER, request.getLotNumber());
        formData.add(PARAM_CAPTCHA_OUTPUT, request.getCaptchaOutput());
        formData.add(PARAM_PASS_TOKEN, request.getPassToken());
        formData.add(PARAM_GEN_TIME, request.getGenTime());
        formData.add(PARAM_CAPTCHA_ID, properties.getCaptchaId());

        // 2. 生成签名并放入
        formData.add(PARAM_SIGN_TOKEN, Geetest4SignUtil.generateSign(formData, properties.getKey()));

        // 3. 构建 HttpEntity，设置 Content-Type
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> httpEntity = new HttpEntity<>(formData, headers);
        try {
            // 4. 调用极验校验接口
            Geetest4ValidateResponse response = HttpUtils.execute(
                    properties.getValidateUrl(),
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {
                    }
            );
            return response != null && SUCCESS.equals(response.getResult());
        } catch (RestClientException e) {
            log.error("极验接口网络异常", e);
            return false;
        }
    }
}
