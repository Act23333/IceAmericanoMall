package org.icedamericanomall.integration.auth.client;

import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.integration.WechatApiException;
import org.icedamericanomall.integration.auth.config.WechatMiniProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WechatMiniApiClient 单元测试 —— 覆写两个 HTTP 接缝（{@code requestStableTokenRaw} /
 * {@code postWxaCodeRaw}）隔离网络，锁定：配置缺失拒绝、token 缓存命中/过期重取、
 * 小程序码二进制透传、errcode JSON 错误体抛 {@code WechatApiException}、scene 长度上限。
 */
@DisplayName("WechatMiniApiClient —— 小程序码客户端（token 缓存 + getwxacodeunlimit）")
class WechatMiniApiClientTest {

    /** PNG 文件魔数（成功响应为图片二进制，不会以 '{' 开头）。 */
    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final String PAGE = "pages/product/detail/index";

    /** 测试桩：覆写 HTTP 接缝，记录调用并按队列回放响应。 */
    private static class StubClient extends WechatMiniApiClient {
        int tokenFetchCount;
        final Deque<String> tokenResponses = new ArrayDeque<>();
        final Deque<byte[]> wxaResponses = new ArrayDeque<>();
        final List<String> requestedTokens = new ArrayList<>();
        final List<String> requestedScenes = new ArrayList<>();
        final List<String> requestedPages = new ArrayList<>();

        StubClient(String appId, String appSecret) {
            super(properties(appId, appSecret));
        }

        static WechatMiniProperties properties(String appId, String appSecret) {
            WechatMiniProperties props = new WechatMiniProperties();
            props.setAppId(appId);
            props.setAppSecret(appSecret);
            return props;
        }

        @Override
        protected String requestStableTokenRaw() {
            tokenFetchCount++;
            return tokenResponses.poll();
        }

        @Override
        protected byte[] postWxaCodeRaw(String accessToken, String scene, String page) {
            requestedTokens.add(accessToken);
            requestedScenes.add(scene);
            requestedPages.add(page);
            return wxaResponses.poll();
        }
    }

    private StubClient client;

    @BeforeEach
    void setUp() {
        client = new StubClient("wx-app-id", "wx-secret");
        client.tokenResponses.offer("{\"access_token\":\"t1\",\"expires_in\":7200}");
    }

    // ==================== 配置守卫 ====================

    @Test
    @DisplayName("getWxaCodeUnlimited — appId/secret 缺失 → BizException「小程序码生成未配置」，不 NPE")
    void shouldRejectBizException_whenMiniNotConfigured() {
        StubClient unconfigured = new StubClient(null, null);

        BizException ex = assertThrows(BizException.class,
                () -> unconfigured.getWxaCodeUnlimited("p=1", PAGE));

        assertTrue(ex.getMessage().contains("小程序码生成未配置"));
        assertEquals(0, unconfigured.tokenFetchCount, "配置缺失不得发起 token 请求");
    }

    // ==================== token 缓存 ====================

    @Test
    @DisplayName("getAccessToken — 有效期内二次获取命中缓存，不重复请求 stable_token")
    void shouldReuseCachedToken_whenWithinValidity() {
        assertEquals("t1", client.getAccessToken());
        assertEquals("t1", client.getAccessToken());

        assertEquals(1, client.tokenFetchCount, "有效期内应命中缓存");
    }

    @Test
    @DisplayName("getAccessToken — expires_in 300s 减余量 300s 后立即过期 → 下次获取重新请求")
    void shouldRefetchToken_whenExpiryReached() {
        client.tokenResponses.clear();
        client.tokenResponses.offer("{\"access_token\":\"t1\",\"expires_in\":300}");
        client.tokenResponses.offer("{\"access_token\":\"t2\",\"expires_in\":7200}");

        assertEquals("t1", client.getAccessToken());
        assertEquals("t2", client.getAccessToken());

        assertEquals(2, client.tokenFetchCount, "过期令牌必须重取");
    }

    // ==================== 小程序码 ====================

    @Test
    @DisplayName("getWxaCodeUnlimited — 成功：PNG 字节透传，scene/page/token 按参传递")
    void shouldReturnPngBytes_whenWxaCodeSuccess() {
        client.wxaResponses.offer(PNG_BYTES);

        byte[] result = client.getWxaCodeUnlimited("p=42", PAGE);

        assertArrayEquals(PNG_BYTES, result);
        assertEquals(1, client.requestedScenes.size());
        assertEquals("p=42", client.requestedScenes.get(0));
        assertEquals(PAGE, client.requestedPages.get(0));
        assertEquals("t1", client.requestedTokens.get(0), "应携带缓存 token 调用 getwxacodeunlimit");
    }

    @Test
    @DisplayName("getWxaCodeUnlimited — 微信返回 errcode JSON → WechatApiException（fail-loud）")
    void shouldThrowWechatApiException_whenErrcodeReturned() {
        client.wxaResponses.offer(
                "{\"errcode\":41030,\"errmsg\":\"invalid page hint: [pages/x]\"}".getBytes(StandardCharsets.UTF_8));

        WechatApiException ex = assertThrows(WechatApiException.class,
                () -> client.getWxaCodeUnlimited("p=1", PAGE));

        assertTrue(ex.getMessage().contains("invalid page hint"));
    }

    @Test
    @DisplayName("getWxaCodeUnlimited — scene 超 32 字符（微信硬限制）→ BizException 参数错误")
    void shouldReject_whenSceneExceedsMaxLength() {
        String overlongScene = "p=" + "a".repeat(31);

        BizException ex = assertThrows(BizException.class,
                () -> client.getWxaCodeUnlimited(overlongScene, PAGE));

        assertTrue(ex.getMessage().contains("scene 超长"));
        assertEquals(0, client.tokenFetchCount, "参数校验应在 token 请求之前");
    }
}
