package org.noLazy.common.client.captcha.impl;

import lombok.extern.slf4j.Slf4j;
import org.noLazy.common.client.captcha.CaptchaClient;
import org.noLazy.common.config.GeetestProperties;
import org.noLazy.common.dto.Geetest4ValidateRequest;
import org.noLazy.common.dto.Geetest4ValidateResponse;
import org.noLazy.common.utils.Geetest4SignUtil;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName: GeetestCaptchaClient
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/26 23:49
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.client.captcha.impl
 */


@Slf4j
@Component
public class GeetestCaptchaClient implements CaptchaClient<Geetest4ValidateRequest> {

    private final GeetestProperties properties;
    private final RestClient restClient;
    //httpclient客户端
    public GeetestCaptchaClient(GeetestProperties properties) {
        this.properties = properties;

        // Apache HttpClient工厂（支持连接池、超时set方法，最稳定）
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(5000);   // 连接超时
        factory.setReadTimeout(5000);      // 读取超时

        this.restClient = RestClient.builder()
                .baseUrl(properties.getValidateUrl())
                .requestFactory(factory)
                .build();
    }
//okHttp客户端
//    public GeetestCaptchaClient(GeetestProperties properties) {
//        this.properties = properties;
//
//        // 1. 创建 OkHttp 工厂
//        OkHttp3ClientHttpRequestFactory factory = new OkHttp3ClientHttpRequestFactory();
//
//        // 2. 设置超时（和你原来的逻辑完全一致）
//        factory.setConnectTimeout(5000);   // 连接超时 5秒
//        factory.setReadTimeout(5000);      // 读取超时 5秒
//
//        // 3. 构建 RestClient
//        this.restClient = RestClient.builder()
//                .baseUrl(properties.getValidateUrl())
//                .requestFactory(factory)
//                .build();
//    }
    @Override
    public boolean verify(Geetest4ValidateRequest request) {
        if (request == null) {
            log.warn("极验验证请求参数为空");
            return false;
        }
        return doValidate(request);
    }

    private boolean doValidate(Geetest4ValidateRequest request) {
        Map<String, String> params = buildParamMap(request);
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

            if (log.isDebugEnabled()) {
                log.debug("极验二次校验结果: {}", response);
            }
            return response != null && "success".equals(response.getResult());
        } catch (RestClientException e) {
            log.error("调用极验接口网络异常", e);
            return false;
        } catch (Exception e) {
            log.error("极验校验过程发生未预期异常", e);
            throw new RuntimeException("极验校验内部错误", e);
        }
    }

    private Map<String, String> buildParamMap(Geetest4ValidateRequest request) {
        Map<String, String> map = new HashMap<>();
        map.put("lot_number", request.getLotNumber());
        map.put("captcha_output", request.getCaptchaOutput());
        map.put("pass_token", request.getPassToken());
        map.put("gen_time", request.getGenTime());
        map.put("captcha_id", properties.getCaptchaId());
        return map;
    }
}
