package org.icedamericanomall.common.utils;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 业务 ID 生成与转换工具（ia-common 共享，ADR-050）—— 全项目业务标识的统一出口。
 *
 * <p><b>方案（ADR-050，B+ 路线）</b>：数据库业务标识列一律 {@code BINARY(16)}，
 * 生成算法收敛为 <b>UUIDv7</b>（RFC 9562：48 位毫秒时间戳 + 74 位随机位）；
 * 对外契约（domain 聚合 / app / adapter / Feign DTO / VO / Req）保持 <b>32 位小写 hex
 * 无连字符</b>（与原 {@code fastSimpleUUID} 格式逐位兼容，API 契约零改动）；
 * {@code byte[]} 形态只存在于持久化边界（PO 字段与仓储 toPO/toDomain 转换）。
 *
 * <p><b>为何持久化边界用 {@code byte[]} 而不是自定义 TypeHandler（关键）</b>：
 * MyBatis-Plus 包装器查询（{@code lambdaQuery().eq(XxxPO::getSkuId, "...")}）的参数值
 * 按运行时类型解析并走 {@code StringTypeHandler}，<b>绕过 {@code @TableField(typeHandler)}</b>
 * ——String 对 {@code BINARY(16)} 列比较永远不相等（<b>静默查空</b>，不报错、最难排查）。
 * 而 {@code byte[]} 是 MyBatis 默认支持类型（{@code ByteArrayTypeHandler}，setBytes/getBytes）：
 * 插入、BaseMapper CRUD、结果映射、包装器 eq/in 全部无需注解、无需 {@code autoResultMap}。
 * 因此：PO 字段声明 {@code byte[]}，包装器 eq/in 的值必须传 {@link #toBytes(String)} 结果。
 *
 * <p><b>为何 UUIDv7 严禁 swap 存储（不要学 {@code UUID_TO_BIN(x, 1)}）</b>：
 * swap（时间低位/时间中位/时间高位字节重排）只对 v1/v6 的
 * time-low｜time-mid｜time-high 布局有意义 —— 重排后才让时间落入高字节获得索引趋势递增。
 * v7 的时间戳<b>本来就在最高 6 字节</b>，原始字节序天然趋势递增；再做 swap 反而把时间戳
 * 挪出高字节、破坏时间序（索引局部性劣化回随机写）。v4 全随机 UUID 连"趋势递增"参数都无效，
 * 这正是本方案用 v7 替换 v4 的核心原因。
 *
 * <p><b>格式契约</b>：
 * <ul>
 *   <li>{@link #nextId()} —— UUIDv7，输出 32 位小写 hex（无连字符），对外唯一口径；</li>
 *   <li>{@link #toBytes(String)} / {@link #toHexString(byte[])} —— 仅限仓储边界转换用，
 *       不得在领域层 / 应用层 / 适配层出现 {@code byte[]} 形态的业务 ID；</li>
 *   <li>{@link #secureToken()} —— 32 字节（256 位熵）SecureRandom hex，<b>专供
 *       refreshToken / jti 等凭据场景</b>：凭据要求不可预测且不可从时间序推断，
 *       绝不允许用时间有序的 v7 生成凭据。</li>
 * </ul>
 *
 * <p>纯 JDK 实现（JDK 21 无内置 v7），零 Spring / MyBatis 依赖；线程安全
 * （{@link SecureRandom} 各方法线程安全，{@link HexFormat} 不可变）。
 */
public final class IdGenerator {

    /** 小写 hex 编解码器（输出格式契约：全项目业务 ID 一律小写）。 */
    private static final HexFormat HEX = HexFormat.of();

    /** 随机源：版本位之外的 74 位随机 + 凭据 256 位熵共用一个线程安全实例。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** RFC 9562 UUIDv7 版本号（byte[6] 高 4 位 = 0111）。 */
    private static final int VERSION_V7_NIBBLE = 0x70;

    /** RFC 9562 保留变体位（byte[8] 高 2 位 = 10）。 */
    private static final int VARIANT_BITS = 0x80;

    /** 掩码：保留 byte[6] 低 4 位随机位（rand_a 高位）。 */
    private static final int LOW_NIBBLE_MASK = 0x0F;

    /** 掩码：保留 byte[8] 低 6 位随机位（rand_b 高位）。 */
    private static final int LOW_SIX_BITS_MASK = 0x3F;

    /** 16 字节 = 128 位业务 ID 的字节数。 */
    private static final int ID_BYTES = 16;

    /** 32 字节 = 256 位凭据熵的字节数。 */
    private static final int TOKEN_BYTES = 32;

    /** 128 位 ID 的 hex 长度（无连字符）。 */
    private static final int HEX_LEN_32 = 32;

    /** 标准连字符 UUID 长度（8-4-4-4-12）。 */
    private static final int HYPHEN_LEN_36 = 36;

    private IdGenerator() {
    }

    /**
     * 生成 UUIDv7 业务 ID（32 位小写 hex，无连字符）。
     *
     * <p>布局（RFC 9562）：byte[0..5] = 当前毫秒时间戳（大端，48 位），byte[6] 高 4 位 =
     * 版本号 0x7，byte[8] 高 2 位 = 变体 0b10，其余为随机位。时间在高字节，
     * 原始字节序趋势递增 —— B+ 树索引写入局部性好。
     *
     * @return 32 位小写 hex 业务 ID（与原 {@code fastSimpleUUID} 输出格式逐位兼容）
     */
    public static String nextId() {
        byte[] bytes = new byte[ID_BYTES];
        RANDOM.nextBytes(bytes);
        long timestamp = System.currentTimeMillis();
        bytes[0] = (byte) (timestamp >>> 40);
        bytes[1] = (byte) (timestamp >>> 32);
        bytes[2] = (byte) (timestamp >>> 24);
        bytes[3] = (byte) (timestamp >>> 16);
        bytes[4] = (byte) (timestamp >>> 8);
        bytes[5] = (byte) timestamp;
        bytes[6] = (byte) ((bytes[6] & LOW_NIBBLE_MASK) | VERSION_V7_NIBBLE);
        bytes[8] = (byte) ((bytes[8] & LOW_SIX_BITS_MASK) | VARIANT_BITS);
        return HEX.formatHex(bytes);
    }

    /**
     * 业务 ID 字符串 → 16 字节原始形态（仅限仓储边界：PO 赋值 / 包装器 eq/in 参数）。
     *
     * @param id 32 位 hex（无连字符）或标准 36 位连字符 UUID（8-4-4-4-12）；大小写均可
     * @return 16 字节；入参为 {@code null} 返回 {@code null}
     * @throws IllegalArgumentException 长度或字符不符合 hex 契约
     */
    public static byte[] toBytes(String id) {
        if (id == null) {
            return null;
        }
        String hex = stripHyphens(id);
        if (hex.length() != HEX_LEN_32) {
            throw new IllegalArgumentException("业务 ID 必须为 32 位 hex 或 36 位连字符 UUID: " + preview(id));
        }
        try {
            return HEX.parseHex(hex);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("业务 ID 含非 hex 字符: " + preview(id));
        }
    }

    /**
     * 宽松解析版 {@link #toBytes(String)} —— 专供<b>外部输入</b>的等值查询入口
     * （URL path 参数、渠道回调携带的单号等）：解析失败返回 {@code null}，
     * 包装器等值比较落空（{@code col = NULL} 永不命中）→ 天然"未命中"语义，
     * 不把垃圾入参放大成 500。仓储的 toPO/toDomain 映射仍用严格的 {@link #toBytes(String)}。
     *
     * @param id 任意外部输入字符串
     * @return 16 字节；{@code null} 或格式非法均返回 {@code null}
     */
    public static byte[] toBytesOrNull(String id) {
        if (id == null) {
            return null;
        }
        try {
            return toBytes(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 16 字节原始形态 → 业务 ID 字符串（32 位小写 hex，无连字符）。
     *
     * @param bytes 数据库读出的 BINARY(16) 原始字节
     * @return 32 位小写 hex；入参为 {@code null} 返回 {@code null}
     * @throws IllegalArgumentException 长度不为 16 字节
     */
    public static String toHexString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length != ID_BYTES) {
            throw new IllegalArgumentException("BINARY(16) 原始字节必须为 16 字节，实际 " + bytes.length);
        }
        return HEX.formatHex(bytes);
    }

    /**
     * 生成 256 位熵安全令牌（64 位小写 hex）—— <b>专供 refreshToken / jti 等凭据场景</b>。
     *
     * <p>凭据的安全前提是不可预测且不可从时间序推断，因此绝不允许用时间有序的
     * {@link #nextId()} 生成凭据；本方法全随机（32 字节 SecureRandom）。
     *
     * @return 64 位小写 hex 随机令牌
     */
    public static String secureToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    /** 标准连字符 UUID（36 位，8-4-4-4-12）→ 去连字符；其余原样返回。 */
    private static String stripHyphens(String id) {
        if (id.length() == HYPHEN_LEN_36
                && id.charAt(8) == '-' && id.charAt(13) == '-'
                && id.charAt(18) == '-' && id.charAt(23) == '-') {
            return id.substring(0, 8) + id.substring(9, 13) + id.substring(14, 18)
                    + id.substring(19, 23) + id.substring(24);
        }
        return id;
    }

    /** 异常消息中的入参预览（截断，防止异常日志被超长入参刷屏）。 */
    private static String preview(String id) {
        String trimmed = id.strip();
        return trimmed.length() <= HEX_LEN_32 ? trimmed : trimmed.substring(0, HEX_LEN_32) + "...";
    }
}
