package org.icedamericanomall.integration.auth.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.HttpUtils;
import org.icedamericanomall.integration.WechatApiException;
import org.icedamericanomall.integration.auth.config.WechatMiniProperties;
import org.icedamericanomall.integration.auth.constants.WechatApiUrlConstant;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序 API 客户端 —— 小程序码生成（{@code wxacode.getUnlimited}）与 stable_token 管理。
 *
 * <p><b>与登录客户端的关系</b>：复用同一份 {@code wechat.mini} 配置（appId/appSecret，
 * {@link WechatMiniProperties}），但职责不同 —— {@code RealWechatMiniClient} 服务于登录
 * （jscode2session），本类服务于 P37 商品分享小程序码（契约 05 §4.4.x）。
 *
 * <p><b>令牌策略</b>：POST {@code cgi-bin/stable_token}（官方稳定版接口，不强制轮换），
 * 内存缓存至 {@code expires_in} 前余量 {@value #TOKEN_EXPIRY_MARGIN_SECONDS} 秒；
 * 双重检查 + synchronized 保证并发下只刷新一次，读路径无锁（volatile 整体替换）。
 *
 * <p><b>失败语义（fail-loud）</b>：配置缺失 → {@code BizException}（"小程序码生成未配置"，
 * 不 NPE）；微信返回 errcode JSON → {@code WechatApiException}。小程序码是分享场景的
 * 显式功能端点（非旁路推荐/降级位），失败必须透出由调用方感知。
 *
 * <p><b>敏感信息</b>：appSecret 仅用于请求体，任何日志不得打印。
 */
@Slf4j
@Component
@EnableConfigurationProperties(WechatMiniProperties.class)
public class WechatMiniApiClient {

    /** 令牌缓存提前失效余量（秒）—— 避免边界时刻拿到已过期令牌。 */
    private static final long TOKEN_EXPIRY_MARGIN_SECONDS = 300;

    /** stable_token 缺省有效期兜底（秒）—— 响应缺 expires_in 时的保守取值。 */
    private static final long DEFAULT_TOKEN_EXPIRY_SECONDS = 7200;

    /** 微信 getwxacodeunlimit 硬限制：scene 最大 32 可见字符。 */
    public static final int SCENE_MAX_LENGTH = 32;

    private static final String GRANT_TYPE_CLIENT_CREDENTIAL = "client_credential";

    /** 小程序码落地版本：正式版。 */
    private static final String WXA_CODE_ENV_VERSION = "release";

    /** JSON 解析器（对齐 {@code WechatCallbackDecoder} 包内先例，静态单例）。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WechatMiniProperties properties;

    /** 令牌内存缓存 —— volatile 整体替换，读无锁；刷新串行在 synchronized 内。 */
    private volatile CachedToken cachedToken;

    public WechatMiniApiClient(WechatMiniProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取小程序 access_token（带内存缓存）。
     *
     * <p>缓存有效（距过期 &gt; {@value #TOKEN_EXPIRY_MARGIN_SECONDS} 秒）直接命中；
     * 过期后并发线程只有一个执行刷新，其余复用刷新结果。
     *
     * @return 有效的 access_token
     * @throws BizException 小程序 appId/appSecret 未配置（1101）
     * @throws WechatApiException 微信侧返回 errcode
     */
    public String getAccessToken() {
        CachedToken current = cachedToken;
        if (current != null && current.validUntilMillis() > System.currentTimeMillis()) {
            return current.accessToken();
        }
        synchronized (this) {
            if (cachedToken != null && cachedToken.validUntilMillis() > System.currentTimeMillis()) {
                return cachedToken.accessToken();
            }
            return refreshAccessToken();
        }
    }

    /**
     * 获取商品分享小程序码（{@code wxacode.getUnlimited}）。
     *
     * <p>scene 由调用方构造（本服务口径 {@code p={productId}}，形如 {@code p=123}，
     * 远小于 32 字符上限）；page 为小程序落地页路径，由调用方配置注入。
     *
     * @param scene 打开小程序后透传的场景参数（≤{@value #SCENE_MAX_LENGTH} 字符）
     * @param page  落地页路径（如 {@code pages/product/detail/index}）
     * @return 小程序码图片二进制（PNG）
     * @throws BizException      配置缺失（1101）/ scene 为空或超长（1101）
     * @throws WechatApiException 微信返回 errcode JSON 错误体
     */
    public byte[] getWxaCodeUnlimited(String scene, String page) {
        validateScene(scene);
        byte[] response = postWxaCodeRaw(getAccessToken(), scene, page);
        return parseWxaCodeResponse(response);
    }

    /** 令牌缓存载体（record 不可变）。 */
    private record CachedToken(String accessToken, long validUntilMillis) {
    }

    private String refreshAccessToken() {
        requireConfigured();
        JsonNode node = parseJson(requestStableTokenRaw(), "微信 stable_token 响应解析失败");
        if (node.has("errcode") && node.get("errcode").asInt() != 0) {
            throw new WechatApiException(ErrorCode.WECHAT_ERROR,
                    "微信 stable_token 获取异常：" + node.path("errmsg").asText());
        }
        long expiresIn = node.path("expires_in").asLong(DEFAULT_TOKEN_EXPIRY_SECONDS);
        long validUntil = System.currentTimeMillis()
                + Math.max(0, expiresIn - TOKEN_EXPIRY_MARGIN_SECONDS) * 1000;
        cachedToken = new CachedToken(node.get("access_token").asText(), validUntil);
        log.info("[WechatMiniApi][stable_token] refreshed, validUntilEpochSec={}", validUntil / 1000);
        return cachedToken.accessToken();
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(properties.getAppId())
                || !StringUtils.hasText(properties.getAppSecret())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "小程序码生成未配置");
        }
    }

    private void validateScene(String scene) {
        if (!StringUtils.hasText(scene)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "小程序码 scene 不能为空");
        }
        if (scene.length() > SCENE_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR, "小程序码 scene 超长（上限 32 字符）");
        }
    }

    /**
     * 解析小程序码响应：成功为图片二进制；失败为 {@code {"errcode":..,"errmsg":..}} JSON。
     * 图片字节流（PNG/JPEG 魔数）不会以 {@code '{'} 开头，据此区分错误体。
     */
    private byte[] parseWxaCodeResponse(byte[] response) {
        if (response == null || response.length == 0) {
            throw new WechatApiException(ErrorCode.WECHAT_ERROR, "微信小程序码返回为空");
        }
        if (looksLikeJson(response)) {
            JsonNode node = parseJson(new String(response, StandardCharsets.UTF_8), "微信小程序码错误体解析失败");
            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                throw new WechatApiException(ErrorCode.WECHAT_ERROR,
                        "微信小程序码生成失败：" + node.path("errmsg").asText());
            }
        }
        return response;
    }

    /** JSON 解析（受检异常归一为 {@code WechatApiException}）。 */
    private static JsonNode parseJson(String json, String failMessage) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new WechatApiException(ErrorCode.WECHAT_ERROR, failMessage, e);
        }
    }

    private boolean looksLikeJson(byte[] bytes) {
        int i = 0;
        while (i < bytes.length && Character.isWhitespace(bytes[i])) {
            i++;
        }
        return i < bytes.length && bytes[i] == '{';
    }

    /**
     * 请求 stable_token（原始 JSON 响应）。测试接缝：单测覆写以隔离网络。
     */
    protected String requestStableTokenRaw() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grant_type", GRANT_TYPE_CLIENT_CREDENTIAL);
        body.put("appid", properties.getAppId());
        body.put("secret", properties.getAppSecret());
        return HttpUtils.postJson(WechatApiUrlConstant.MINI_STABLE_TOKEN_URL, body);
    }

    /**
     * 请求小程序码（原始字节响应）。测试接缝：单测覆写以隔离网络。
     */
    protected byte[] postWxaCodeRaw(String accessToken, String scene, String page) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", scene);
        if (StringUtils.hasText(page)) {
            body.put("page", page);
        }
        body.put("check_path", false);
        body.put("env_version", WXA_CODE_ENV_VERSION);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return HttpUtils.execute(
                URI.create(WechatApiUrlConstant.MINI_WXA_CODE_UNLIMITED_URL + accessToken),
                HttpMethod.POST, new HttpEntity<>(body, headers), byte[].class);
    }
}
