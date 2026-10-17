package org.icedamericanomall.ai.domain.model.conversation;

import org.icedamericanomall.common.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话键口径测试（ADR-063 §决策 7 表 #4，批次 B7b）—— 锁定<b>唯一派生算法</b>与<b>列宽预算</b>。
 *
 * <p>关键算式（这是本项要求的由来）：
 * 旧形态 {@code buyer_{buyerId}_seller_{sellerId}_product_{productId}} 在业务键化后
 * 取最小 id 也是 <b>57 字符</b>，而 {@code chat_message.conversation_id} 是 <b>VARCHAR(36)</b>
 * ⇒ 超限 21 字符。派生值固定 32 字符，留 4 位余量。
 */
@DisplayName("ConversationId —— 内容派生 / 列宽预算 / 旧形态归一")
class ConversationIdTest {

    @Test
    @DisplayName("derive — 同三元组恒得同号（会话幂等合并的前提），且为 32 位小写 hex")
    void shouldBeStableForSameTriple() {
        String first = ConversationId.derive(1L, 2L, "0f1e2d3c4b5a69788796a5b4c3d2e1f0");
        String again = ConversationId.derive(1L, 2L, "0f1e2d3c4b5a69788796a5b4c3d2e1f0");

        assertEquals(first, again);
        assertTrue(first.matches("^[0-9a-f]{32}$"), first);
    }

    @Test
    @DisplayName("derive — 三元组任一不同即得不同号（不得把不同会话折叠成同一个）")
    void shouldDifferForDifferentTriple() {
        String base = ConversationId.derive(1L, 2L, "spu-a");
        assertNotEquals(base, ConversationId.derive(9L, 2L, "spu-a"));
        assertNotEquals(base, ConversationId.derive(1L, 9L, "spu-a"));
        assertNotEquals(base, ConversationId.derive(1L, 2L, "spu-b"));
    }

    @Test
    @DisplayName("derive — 无商品上下文 → 占位成分（纯咨询会话也能稳定归组）")
    void shouldHandleMissingProduct() {
        assertEquals(ConversationId.derive(1L, 2L, null), ConversationId.derive(1L, 2L, ""));
        assertTrue(ConversationId.derive(1L, 2L, null).matches("^[0-9a-f]{32}$"));
    }

    @Test
    @DisplayName("capacity — 派生值 32 字符 ≤ chat_message.conversation_id VARCHAR(36)")
    void shouldFitColumn() {
        String derived = ConversationId.derive(1L, 2L, "0f1e2d3c4b5a69788796a5b4c3d2e1f0");
        assertEquals(ConversationId.LENGTH, derived.length());
        assertTrue(derived.length() <= ConversationId.MAX_LENGTH);
        // 反证：旧复合串在业务键化后必然超列宽（21 字符），这正是本项要改的原因
        String legacy = "buyer_1_seller_2_product_0f1e2d3c4b5a69788796a5b4c3d2e1f0";
        assertTrue(legacy.length() > ConversationId.MAX_LENGTH, "旧形态长度 = " + legacy.length());
    }

    @Test
    @DisplayName("normalize — 旧复合串 → 重算成 32 位派生值（修的是「57 字符塞不进 36 列」的真缺陷）")
    void shouldRewriteLegacyForm() {
        String normalized = ConversationId.normalize("buyer_1_seller_2_product_0f1e");

        assertEquals(ConversationId.derive(1L, 2L, "0f1e"), normalized);
        assertEquals(ConversationId.LENGTH, normalized.length());
    }

    @Test
    @DisplayName("normalize — 已归一值幂等（重复发送不会产生第二个会话）")
    void shouldBeIdempotentOnCanonical() {
        String derived = ConversationId.derive(1L, 2L, "spu-a");
        assertEquals(derived, ConversationId.normalize(derived));
        assertEquals(derived, ConversationId.normalize("  " + derived + "  "));
    }

    @Test
    @DisplayName("normalize — 其它任意串原样保留（fail-open：历史/自定义会话键不得因归一而上丢消息）")
    void shouldPassThroughUnknownForm() {
        assertEquals("c-1", ConversationId.normalize("c-1"));
    }

    @Test
    @DisplayName("normalize — 空会话键 → 400 PARAM_ERROR（无法凭空构造三元组）")
    void shouldRejectBlank() {
        assertEquals(1101, assertThrows(BadRequestException.class,
                () -> ConversationId.normalize(" ")).getErrorCode().getCode());
    }
}
