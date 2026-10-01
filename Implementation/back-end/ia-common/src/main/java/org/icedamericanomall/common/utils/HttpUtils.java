package org.icedamericanomall.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.HttpException;
import org.icedamericanomall.common.interceptor.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 生产级 HTTP 工具类
 * Spring Boot 3.2+ / Spring 6.1 + Apache HttpClient 5.x + RestClient
 */
public final class HttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);
    private static final RestClient REST_CLIENT;

    // 超时（毫秒）
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int SOCKET_TIMEOUT_MS = 10_000;
    private static final int CONNECTION_REQUEST_TIMEOUT_MS = 3_000;

    // 连接池
    private static final int MAX_TOTAL = 200;
    private static final int MAX_PER_ROUTE = 50;
    private static final int TTL_MINUTES = 5;

    static {
        // 1. 连接池
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(MAX_TOTAL);
        cm.setDefaultMaxPerRoute(MAX_PER_ROUTE);
        cm.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT_MS))
                .setSocketTimeout(Timeout.ofMilliseconds(SOCKET_TIMEOUT_MS))
                .setTimeToLive(Timeout.ofMinutes(TTL_MINUTES))
                .build());

        // 2. 请求配置
        RequestConfig rc = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECTION_REQUEST_TIMEOUT_MS))
                .build();

        // 3. HttpClient
        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(rc)
                .build();

        // 4. RequestFactory
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        // 5. 显式注入统一 ObjectMapper
        ObjectMapper objectMapper = SpringContextHolder.getBean(ObjectMapper.class);
        MappingJackson2HttpMessageConverter jacksonConverter =
                new MappingJackson2HttpMessageConverter(objectMapper);

        // 6. RestClient
        REST_CLIENT = RestClient.builder()
                .requestFactory(factory)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(jacksonConverter);
                })
                .requestInterceptor(new HttpLoggingInterceptor())
                .build();
    }

    private HttpUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 暴露底层 RestClient，供 Multipart / SSE 等特殊场景扩展 */
    public static RestClient getRestClient() {
        return REST_CLIENT;
    }

    // ==================== GET ====================

    public static String get(String url) {
        return get(url, null, null);
    }

    public static String get(String url, Map<String, String> headers) {
        return get(url, headers, null);
    }

    public static String get(String url, Map<String, String> headers, Map<String, Object> params) {
        return execute(buildUri(url, params), HttpMethod.GET, headerEntity(headers), String.class);
    }

    // ==================== POST JSON ====================

    public static String postJson(String url, Object body) {
        return postJson(url, body, null);
    }

    public static String postJson(String url, Object body, Map<String, String> headers) {
        return execute(URI.create(url), HttpMethod.POST, jsonEntity(body, headers), String.class);
    }

    // ==================== POST FORM ====================

    public static String postForm(String url, Map<String, Object> form) {
        return postForm(url, form, null);
    }

    public static String postForm(String url, Map<String, Object> form, Map<String, String> headers) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (headers != null) h.setAll(headers);

        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.setAll(form);
        return execute(URI.create(url), HttpMethod.POST, new HttpEntity<>(map, h), String.class);
    }

    // ==================== PUT ====================

    public static String put(String url, Object body) {
        return put(url, body, null);
    }

    public static String put(String url, Object body, Map<String, String> headers) {
        return execute(URI.create(url), HttpMethod.PUT, jsonEntity(body, headers), String.class);
    }

    // ==================== DELETE ====================

    public static String delete(String url) {
        return delete(url, null);
    }

    public static String delete(String url, Map<String, String> headers) {
        return execute(URI.create(url), HttpMethod.DELETE, headerEntity(headers), String.class);
    }

    // ==================== 核心执行：Class<T> ====================

    public static <T> T execute(String url, HttpMethod method, HttpEntity<?> entity, Class<T> type) {
        return execute(URI.create(url), method, entity, type);
    }

    public static <T> T execute(URI uri, HttpMethod method, HttpEntity<?> entity, Class<T> type) {
        try {
            return executeRequest(uri, method, entity).toEntity(type).getBody();
        } catch (Exception e) {
            throw convert(e, method, uri);
        }
    }

    // ==================== 核心执行：ParameterizedTypeReference<T> ====================

    public static <T> T execute(String url, HttpMethod method, HttpEntity<?> entity,
                                ParameterizedTypeReference<T> typeRef) {
        return execute(URI.create(url), method, entity, typeRef);
    }

    public static <T> T execute(URI uri, HttpMethod method, HttpEntity<?> entity,
                                ParameterizedTypeReference<T> typeRef) {
        try {
            return executeRequest(uri, method, entity).toEntity(typeRef).getBody();
        } catch (Exception e) {
            throw convert(e, method, uri);
        }
    }

    // ==================== 内部：发请求，返回 ResponseSpec ====================

    private static RestClient.ResponseSpec executeRequest(URI uri,
                                                          HttpMethod method,
                                                          HttpEntity<?> entity) {
        RestClient.RequestBodySpec spec = REST_CLIENT.method(method).uri(uri)
                .headers(h -> {
                    if (entity != null) {
                        h.putAll(entity.getHeaders());
                    }
                });

        if (entity != null && entity.getBody() != null) {
            return spec.body(entity.getBody()).retrieve();
        }
        return spec.retrieve();
    }

    // ==================== 内部：异常转换 ====================

    private static HttpException convert(Exception e, HttpMethod method, URI uri) {
        if (e instanceof RestClientResponseException re) {
            String msg = "HTTP %s %s failed, status: %d, body: %s"
                    .formatted(method, uri, re.getStatusCode().value(), re.getResponseBodyAsString());
            logger.error(msg, re);
            return new HttpException(ErrorCode.THIRD_PARTY_ERROR, msg, re);
        }
        String msg = "HTTP %s %s exception: %s".formatted(method, uri, e.getMessage());
        logger.error(msg, e);
        return new HttpException(ErrorCode.THIRD_PARTY_ERROR, msg, e);
    }

    // ==================== 内部：URI / Entity 构造 ====================

    private static URI buildUri(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return URI.create(url);
        }
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(url);
        params.forEach((k, v) -> {
            if (v != null) b.queryParam(k, v);
        });
        return b.build().encode(StandardCharsets.UTF_8).toUri();
    }

    /** header-only entity（GET / DELETE 用） */
    private static HttpEntity<?> headerEntity(Map<String, String> headers) {
        HttpHeaders h = new HttpHeaders();
        if (headers != null) h.setAll(headers);
        return new HttpEntity<>(h);
    }

    /** JSON body entity（POST / PUT 用） */
    private static HttpEntity<Object> jsonEntity(Object body, Map<String, String> headers) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (headers != null) h.setAll(headers);
        return new HttpEntity<>(body, h);
    }
}