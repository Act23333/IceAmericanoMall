package org.icedamericanomall.common.interceptor;
import org.jspecify.annotations.NonNull; // 引入 JSpecify 的 NonNull
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 统一 HTTP 埋点拦截器
 * 注意：Spring 6.1+ 使用 JSpecify，接口所在包已 @NullMarked，
 * 参数与返回值默认非空，无需再手动加 @NonNull。
 */
public class HttpLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(HttpLoggingInterceptor.class);

    private static final String TRACE_ID = "traceId";

    @Override
    @SuppressWarnings("NullableProblems") // 直接加在方法上，或者加在整个类上
    public ClientHttpResponse intercept(@NonNull HttpRequest request,
                                        byte @NonNull [] body,
                                        @NonNull ClientHttpRequestExecution execution) throws IOException {
        long start = System.currentTimeMillis();
        String traceId = MDC.get(TRACE_ID);

        ClientHttpResponse response = null;
        try {
            response = execution.execute(request, body);
            return response;
        } finally {
            int status = -1;
            if (response != null) {
                try {
                    status = response.getStatusCode().value();
                } catch (Exception ignored) {
                    // 读取状态码失败时忽略
                }
            }
            logger.info("HTTP_OUT method={} uri={} status={} cost={}ms traceId={}",
                    request.getMethod().name(),
                    request.getURI(),
                    status,
                    System.currentTimeMillis() - start,
                    traceId);
        }
    }
}