package org.icedamericanomall.ai.infrastructure.adapter.tool;

import org.icedamericanomall.ai.domain.model.spu.SpuDetailInfo;
import org.icedamericanomall.ai.domain.port.SpuDetailPort;
import org.icedamericanomall.common.api.ErrorInfo;
import org.icedamericanomall.common.enums.ErrorCategory;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.exception.UpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link SpuDetailTool} 的用户可见措辞 —— 本工具产出的字符串<b>原样进 LLM 上下文</b>，
 * 所以它就是「用户最终听到什么」的最后一个决定点。
 *
 * <p><b>★ 本类锁死的缺陷（{@code collaboration/09-constraints.md} §三·八·1 点名的反例）</b>：
 * 下游 404「商品不存在」曾被两层翻译成「商品服务暂不可用」，AI 助手于是对用户说
 * 「服务故障」。改后按错误体自带的 {@code category} 分流：{@code CLIENT} → 直说「不存在」；
 * 只有 {@code TRANSIENT}/{@code BUG} 才说「暂时无法获取」（<b>不得</b>说「服务不可用」，§三·八·2）。
 */
@ExtendWith(MockitoExtension.class)
class SpuDetailToolTest {

    private static final String SPU_ID = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";

    @Mock
    private SpuDetailPort spuDetailPort;

    private SpuDetailTool tool;

    @BeforeEach
    void setUp() {
        tool = new SpuDetailTool(spuDetailPort);
    }

    private static UpstreamException upstream(int code, String msg, ErrorCategory category, int status) {
        return new UpstreamException(ErrorInfo.of(code, msg, category, "trace-up-1"), status);
    }

    @Test
    @DisplayName("正常 — 查到详情 → 渲染名称/品牌/描述/销量")
    void shouldRenderDetail_whenFound() {
        when(spuDetailPort.getSpuDetail(SPU_ID)).thenReturn(new SpuDetailInfo(
                SPU_ID, "Nike ZoomX", "Nike", "轻量跑鞋", 1523, "颜色:白"));

        String text = tool.getSpuDetail(SPU_ID);

        assertTrue(text.contains("Nike ZoomX"));
        assertTrue(text.contains(SPU_ID));
        assertTrue(text.contains("1523"));
    }

    @Test
    @DisplayName("未找到 — 端口返回 null → 「未找到商品」")
    void shouldRenderNotFound_whenNull() {
        when(spuDetailPort.getSpuDetail(SPU_ID)).thenReturn(null);

        assertTrue(tool.getSpuDetail(SPU_ID).contains("未找到"));
    }

    // ==================== ★ 核心：404 必须说「不存在」，不得说「不可用」 ====================

    @Test
    @DisplayName("★ 下游 404（category=CLIENT, code=1103）→ 输出「商品不存在」，且不含「不可用/繁忙」")
    void shouldSayNotFound_whenDownstreamClientError404() {
        when(spuDetailPort.getSpuDetail(SPU_ID))
                .thenThrow(upstream(1103, "商品不存在或已删除", ErrorCategory.CLIENT, 404));

        String text = tool.getSpuDetail(SPU_ID);

        assertTrue(text.contains("商品不存在"), "用户/AI 必须看到业务事实：实际输出=" + text);
        assertFalse(text.contains("不可用"), "不得把「不存在」说成「服务不可用」：实际输出=" + text);
        assertFalse(text.contains("繁忙"), "不得把「不存在」说成「系统繁忙」：实际输出=" + text);
        assertTrue(text.contains(SPU_ID), "文案应带 spuId 便于用户核对");
    }

    @Test
    @DisplayName("400（spuId 形态非法，category=CLIENT）→ 提示「参数无效」，仍是业务措辞")
    void shouldSayInvalidParam_whenDownstreamClientError400() {
        when(spuDetailPort.getSpuDetail("bad-id"))
                .thenThrow(upstream(1101, "参数错误: spuId 非 32-hex", ErrorCategory.CLIENT, 400));

        String text = tool.getSpuDetail("bad-id");

        assertTrue(text.contains("参数无效"), "实际输出=" + text);
        assertFalse(text.contains("不可用"));
    }

    // ==================== 真正的故障仍说「暂时无法获取」 ====================

    @Test
    @DisplayName("下游 503（TRANSIENT）→ 「暂时无法获取」，不得写「服务不可用」（§三·八·2）")
    void shouldSayTemporarilyUnavailable_whenTransient() {
        when(spuDetailPort.getSpuDetail(SPU_ID))
                .thenThrow(upstream(1001, "系统繁忙", ErrorCategory.TRANSIENT, 503));

        String text = tool.getSpuDetail(SPU_ID);

        assertTrue(text.contains("暂时无法获取"), "实际输出=" + text);
        assertFalse(text.contains("服务不可用"), "§三·八·2 明确禁止该措辞：实际输出=" + text);
    }

    @Test
    @DisplayName("下游 500（BUG）→ 仍是「暂时无法获取」，不向用户泄露内部细节")
    void shouldSayTemporarilyUnavailable_whenBug() {
        when(spuDetailPort.getSpuDetail(SPU_ID))
                .thenThrow(upstream(1000, "服务器内部错误", ErrorCategory.BUG, 500));

        String text = tool.getSpuDetail(SPU_ID);

        assertTrue(text.contains("暂时无法获取"));
        assertFalse(text.contains("服务器内部错误"), "BUG 详情不得进 LLM 上下文");
    }

    @Test
    @DisplayName("非上游异常（端口实现其他故障）→ 「暂时无法获取」，不抛异常给 Agent")
    void shouldStayResilient_whenPortThrowsOtherwise() {
        when(spuDetailPort.getSpuDetail(SPU_ID)).thenThrow(new IllegalStateException("boom"));

        String text = tool.getSpuDetail(SPU_ID);

        assertTrue(text.contains("暂时无法获取"));
        assertFalse(text.contains("boom"));
    }

    @Test
    @DisplayName("SYSTEM_BUSY 语义的 BizException（降级工厂产物）→ 「暂时无法获取」")
    void shouldSayTemporarilyUnavailable_whenDegradedByFallback() {
        when(spuDetailPort.getSpuDetail(SPU_ID)).thenThrow(
                new org.icedamericanomall.common.exception.BizException(
                        ErrorCode.SYSTEM_BUSY, "商品详情暂时无法获取，请稍后重试"));

        String text = tool.getSpuDetail(SPU_ID);

        assertTrue(text.contains("暂时无法获取"));
    }
}
