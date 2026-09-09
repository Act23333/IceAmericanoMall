package org.icedamericanomall.integration.wechat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.noLazy.common.exception.BizException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockWechatOAuthClient 单元测试 —— 确定性虚拟 openid + 参数校验。
 */
@DisplayName("MockWechatOAuthClient 单元测试")
class MockWechatOAuthClientTest {

    private final MockWechatOAuthClient client = new MockWechatOAuthClient();

    @Test
    @DisplayName("同一 code 恒定映射到同一 openid，且昵称/头像非空")
    void shouldReturnDeterministicOpenid() {
        WechatUserInfo a = client.getUserInfoByCode("code-abc");
        WechatUserInfo b = client.getUserInfoByCode("code-abc");

        assertNotNull(a.getOpenid());
        assertEquals(a.getOpenid(), b.getOpenid(), "同一 code 应得到同一 openid");
        assertTrue(a.getOpenid().startsWith("mock_openid_"));
        assertNotNull(a.getNickname());
        assertNotNull(a.getAvatarUrl());
    }

    @Test
    @DisplayName("不同 code 通常映射到不同 openid")
    void shouldDifferentiateByCode() {
        assertNotEquals(client.getUserInfoByCode("code-1").getOpenid(),
                client.getUserInfoByCode("code-2").getOpenid());
    }

    @Test
    @DisplayName("空 code 抛出异常")
    void shouldThrow_whenBlankCode() {
        assertThrows(BizException.class, () -> client.getUserInfoByCode(""));
        assertThrows(BizException.class, () -> client.getUserInfoByCode(null));
    }
}
