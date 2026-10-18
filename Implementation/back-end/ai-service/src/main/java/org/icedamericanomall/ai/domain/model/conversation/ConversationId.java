package org.icedamericanomall.ai.domain.model.conversation;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.utils.crypto.Sha256Util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会话键（{@code chat_message.conversation_id}）—— <b>内容派生值</b>，不是标识、不是单号
 * （纯 POJO，零框架依赖；ADR-063 §决策 7 表 #4 / 契约 §1.9⑦-4，批次 B7b）。
 *
 * <p><b>为什么必须改口径（长度算术，不是洁癖）</b>：旧形态
 * {@code buyer_{buyerId}_seller_{sellerId}_product_{productId}} 在业务键化后是 <b>57 字符</b>，
 * 而列宽是 {@code VARCHAR(36)} ⇒ <b>超限 21 字符</b>（MySQL 严格模式下直接插入失败）。
 * 新形态 {@code sha256("<buyerId>|<sellerId>|<spuId>")} 取<b>前 32 位小写 hex</b>：
 * 同三元组恒得同号（会话幂等合并的前提），32 字符填进 36 位列宽并留 4 位余量，
 * <b>不需要映射表</b>。
 *
 * <p><b>唯一派生点（服务端）</b>：{@link #derive(Long, Long, String)} 是全仓唯一的算法定义；
 * 写入路径（{@code ChatMessageManager#sendMessage}）是唯一的<b>归一化/派生点</b>。此前该串
 * 「由调用方自造」，实测代码与前端都<b>没有</b>构造方（仅 javadoc 与测试桩出现），
 * 故本批显式把派生收到服务端，而不是继续留一个「谁都能拼」的字符串约定。
 *
 * <p><b>会话键口径（唯一）</b>：{@code chat_message.conversation_id} —— {@code VARCHAR(36)}，
 * <b>内容派生 32-hex</b>（本类），断言列宽见 {@link #MAX_LENGTH}。
 *
 * <p><b>已消除的「两套口径」隐患</b>：此前另有一张 {@code ai_conversation} 表用
 * {@code conversation_id BINARY(16)}（UUIDv7 原始字节）承载同名不同型、不同算法的会话键，
 * 且<b>零代码接入</b>。该表已于 2026-10-18（U3 裁决）从 {@code Initialize.sql} 删除 ——
 * 同一名词两套算法的静默不兼容风险随之消失，不要再按「两套并存」写代码或注释。
 */
public final class ConversationId {

    /** 派生值长度（32 位小写 hex = 128 bit，足以避免会话键碰撞）。 */
    public static final int LENGTH = 32;

    /** {@code chat_message.conversation_id} 的列宽（{@code VARCHAR(36)}）—— 派生值必须装得下。 */
    public static final int MAX_LENGTH = 36;

    /** 三元组分隔符（改动即令全部已有会话键失效，属破坏性变更）。 */
    private static final String SEPARATOR = "|";

    /** 无商品上下文时的占位成分（会话可与具体商品无关，如纯咨询）。 */
    private static final String NO_PRODUCT = "-";

    /** 旧形态：{@code buyer_{id}_seller_{id}_product_{id}}（仅用于兼容归一，不再产出）。 */
    private static final Pattern LEGACY_FORM =
            Pattern.compile("^buyer_(\\d+)_seller_(\\d+)_product_(.+)$");

    /** 已归一形态：32 位小写 hex（幂等：再次归一原样返回）。 */
    private static final Pattern CANONICAL_FORM = Pattern.compile("^[0-9a-f]{" + LENGTH + "}$");

    private ConversationId() {
    }

    /**
     * <b>唯一派生算法</b>：{@code sha256("<buyerId>|<sellerId>|<spuId>")} 的前 32 位小写 hex。
     *
     * @param buyerId 买家 user_id
     * @param sellerId 商家 user_id
     * @param spuId   SPU 标识（业务键 32-hex 或历史技术键；null/空 → 占位 {@code "-"}）
     * @return 32 位小写 hex 会话键
     */
    public static String derive(Long buyerId, Long sellerId, String spuId) {
        String product = (spuId == null || spuId.isBlank()) ? NO_PRODUCT : spuId.strip();
        return Sha256Util.hexPrefix(buyerId + SEPARATOR + sellerId + SEPARATOR + product, LENGTH);
    }

    /**
     * 服务端归一（唯一派生点）：把入参会话键收敛到新口径。
     *
     * <p>三种情形，语义各不相同（都<b>不抛异常</b>，因为「会话键」不是资源寻址键）：
     * <ol>
     *   <li><b>已归一（32 位小写 hex）</b> → 原样返回（幂等，重复发送不会产生第二个会话）；</li>
     *   <li><b>旧形态复合串</b> → 解析三元组后 {@link #derive} 重算（这条分支修的是
     *       「57 字符塞不进 VARCHAR(36)」的真实缺陷）；</li>
     *   <li><b>其它任意串</b> → <b>原样保留</b>（fail-open：历史/自定义会话键不能因归一而上丢消息；
     *       新会话<b>必须</b>由服务端 {@link #derive} 生成，不得再约定「调用方自造串」）。</li>
     * </ol>
     *
     * @param raw 客户端/上游传入的会话键
     * @return 归一后的会话键
     * @throws BadRequestException 会话键为空（无法凭空构造三元组）
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, "会话键不能为空");
        }
        String candidate = raw.strip();
        if (CANONICAL_FORM.matcher(candidate).matches()) {
            return candidate;
        }
        Matcher legacy = LEGACY_FORM.matcher(candidate);
        if (legacy.matches()) {
            return derive(Long.valueOf(legacy.group(1)), Long.valueOf(legacy.group(2)), legacy.group(3));
        }
        return candidate;
    }
}
