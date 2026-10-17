package org.icedamericanomall.common.utils.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 无密钥 SHA-256 摘要工具（ia-common 共享内核，批次 B7b 新增）—— <b>承载层「内容派生值」的唯一出口</b>。
 *
 * <p><b>与 {@link BlindIndexUtil} 的分工（勿混用）</b>：
 * <ul>
 *   <li>{@link BlindIndexUtil} 是 <b>HMAC-SHA256 盲索引</b>：带密钥、用于 PII 等值查询，
 *       <b>不可逆且不可离线穷举</b>；</li>
 *   <li>本类是无密钥摘要：用于<b>内容寻址</b>（对象键 / 会话键），要求「同内容恒得同值」，
 *       且<b>不承载任何秘密</b> —— 输出会出现在对象键与库列里，可被观察。</li>
 * </ul>
 *
 * <p><b>禁用场景（硬约束）</b>：不得用于密码、令牌、PII 的不可逆存储 —— 无密钥摘要对
 * 低熵输入（手机号等）可被彩虹表反查。
 *
 * <p>调用面（2026-10-18 实测）：商品分享小程序码对象键（{@code SpuShareManager}，取前 16 位）
 * 与买卖家会话键（{@code ConversationId}，取前 32 位）。两处都属 ADR-063 §决策 7 所指
 * 「承载位放内容派生值」。
 */
public final class Sha256Util {

    /** SHA-256 摘要长度（32 字节 = 64 位 hex）。 */
    private static final int MAX_HEX_LENGTH = 64;

    /** 前缀最短长度（太短则与「自增序号」无差别，失去内容寻址意义）。 */
    private static final int MIN_PREFIX_LENGTH = 8;

    private Sha256Util() {
    }

    /**
     * 全文 SHA-256（64 位小写 hex）。
     *
     * @param plain 原文（UTF-8 编码）；{@code null} 返回 {@code null}
     */
    public static String hex(String plain) {
        return hexPrefix(plain, MAX_HEX_LENGTH);
    }

    /**
     * 取 SHA-256 前 {@code length} 位小写 hex（承载位定长的唯一出口）。
     *
     * <p><b>为何截断是安全的</b>：承载位只需要「同内容恒得同键、不同内容几乎不撞」，
     * 不需要完整 256 位；16 位 hex = 64 bit、32 位 hex = 128 bit，碰撞概率远低于
     * 承载位自身的规模（对象数 / 会话数）。
     *
     * @param plain  原文（UTF-8）；{@code null} 返回 {@code null}
     * @param length 期望长度（{@value #MIN_PREFIX_LENGTH}–{@value #MAX_HEX_LENGTH}）
     * @throws IllegalArgumentException 长度越界
     */
    public static String hexPrefix(String plain, int length) {
        if (plain == null) {
            return null;
        }
        if (length < MIN_PREFIX_LENGTH || length > MAX_HEX_LENGTH) {
            throw new IllegalArgumentException("SHA-256 截断长度必须在 " + MIN_PREFIX_LENGTH
                    + "~" + MAX_HEX_LENGTH + " 之间: " + length);
        }
        return HexFormat.of().formatHex(digest(plain.getBytes(StandardCharsets.UTF_8)))
                .substring(0, length);
    }

    private static byte[] digest(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            // JDK 规范强制提供 SHA-256；走到这里说明运行环境被裁剪，属不可恢复的系统级故障
            throw new IllegalStateException("当前 JDK 不提供 SHA-256 实现", e);
        }
    }
}
