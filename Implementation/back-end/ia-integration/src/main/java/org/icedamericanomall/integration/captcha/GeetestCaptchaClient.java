package org.icedamericanomall.integration.captcha;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.integration.captcha.GeetestProperties;
import org.noLazy.common.dto.Geetest4ValidateRequest;
import org.noLazy.common.dto.Geetest4ValidateResponse;
import org.noLazy.common.utils.Geetest4SignUtil;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

/**
 * 极验 v4 人机验证客户端。
 */
@Slf4j
@Component
public class GeetestCaptchaClient implements CaptchaClient<Geetest4ValidateRequest> {

    private final GeetestProperties properties;
    private final RestClient restClient;

    public GeetestCaptchaClient(GeetestProperties properties) {
        this.properties = properties;
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restClient = RestClient.builder()
                .baseUrl(properties.getValidateUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public boolean verify(Geetest4ValidateRequest request) {
        if (request == null) { log.warn("极验验证请求参数为空"); return false; }
        return doValidate(request);
    }

    private boolean doValidate(Geetest4ValidateRequest request) {
        Map<String, String> params = new HashMap<>();
        params.put("lot_number", request.getLotNumber());
        params.put("captcha_output", request.getCaptchaOutput());
        params.put("pass_token", request.getPassToken());
        params.put("gen_time", request.getGenTime());
        params.put("captcha_id", properties.getCaptchaId());

        String signToken = Geetest4SignUtil.generateSign(params, properties.getKey());
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        params.forEach(formData::add);
        formData.add("sign_token", signToken);

        try {
            Geetest4ValidateResponse response = restClient.post()
                    .uri("/validate")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Geetest4ValidateResponse.class);
            return response != null && "success".equals(response.getResult());
        } catch (RestClientException e) {
            log.error("极验接口网络异常", e);
            return false;
        }
    }
}
