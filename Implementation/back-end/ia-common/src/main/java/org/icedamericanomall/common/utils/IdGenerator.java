package org.icedamericanomall.common.utils;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;

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
 *   <li>{@link #toWire(byte[])} / {@link #parseWire(String)} —— <b>编解码唯一出口</b>
 *       （ADR-062 §决策 1/7：出方向 {@code toWire}、入方向 {@code parseWire}），
 *       两方向互逆（{@code parseWire(toWire(b))} 逐字节等于 {@code b}）；
 *       仓储层不得各自实现 {@code byte[]} ↔ {@code String} 的手工转换；</li>
 *   <li>{@link #requireWire(String, String)} —— 适配层入参的<b>形态校验唯一口</b>：
 *       合法返回归一化 32-hex，非法抛 {@code PARAM_ERROR}（HTTP 400）<b>fail-fast</b>，
 *       绝不把非法形态静默变成「查不到」；</li>
 *   <li>{@link #toBytes(String)} / {@link #toHexString(byte[])} —— 前两者的内核，
 *       保留供仓储边界直接调用；{@code byte[]} 形态的业务 ID <b>不得</b>出现在
 *       适配层（{@code adapter/} 包内只许 {@code String} 形态）；</li>
 *   <li>{@link #toBytesOrNull(String)} —— <b>宽松语义，只许内部防御用</b>
 *       （仓储等值查询的「未命中」归一、已通过适配层形态校验后的二次解析）；
 *       <b>不得</b>作为线格式入口的解析路径（ADR-062 §决策 7：客户端拼错一个 hex
 *       字符应得到 400，而不是 404/空列表）；</li>
 *   <li>{@link #secureToken()} —— 32 字节（256 位熵）SecureRandom hex，<b>专供
 *       refreshToken / jti 等凭据场景</b>：凭据要求不可预测且不可从时间序推断，
 *       绝不允许用时间有序的 v7 生成凭据；</li>
 *   <li>{@link #randomHex(int)} —— 指定长度的纯随机 hex <b>短串</b>，专供「对外渠道单号」
 *       （如微信 {@code out_trade_no} / {@code out_refund_no}）：对外单号既要短（渠道协议上限）、
 *       又不得可从时间序推断，因此不能用 v7，也不复用凭据 64 位全量。</li>
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

    /** 凭据 hex 长度（32 字节 = 64 位 hex），同时是 {@link #randomHex(int)} 的长度上限。 */
    private static final int TOKEN_HEX_LEN = TOKEN_BYTES * 2;

    /** {@link #randomHex(int)} 的最小长度（8 位 hex = 32 bit 熵，再短即不具抗碰撞性）。 */
    private static final int MIN_RANDOM_HEX_LEN = 8;

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
     * 宽松解析版 {@link #toBytes(String)} —— <b>只许内部防御用</b>：仓储等值查询
     * （解析失败返回 {@code null}，包装器等值比较落空 {@code col = NULL} 永不命中 → 天然
     * 「未命中」语义，不把垃圾入参放大成 500），或用在对「已通过适配层形态校验」的入参
     * 做二次解析之后。
     *
     * <p><b>不得作为线格式入口的解析路径</b>（ADR-062 §决策 7 / §守卫 4）：适配层的
     * 外部入参（路径变量 / 查询参数 / 请求体）必须先过 {@link #requireWire(String, String)}，
     * 非法即 400 fail-fast；把宽松语义当入口，等于「客户端拼错一个字符 → 静默变成查不到」，
     * 排查成本全部转嫁给调用方。
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

    /**
     * 生成指定长度的<b>纯随机</b> hex 短串 —— 专供「对外渠道单号」等<b>需要短、
     * 且不得从时间序推断</b>的对外标识（如 {@code pay_order.channel_trade_no} 对应微信
     * {@code out_trade_no}）。
     *
     * <p><b>为什么不复用 {@link #nextId()}</b>：UUIDv7 的前 48 位就是毫秒时间戳 —— 交给
     * 渠道/第三方后，任何人都能从单号反推下单时刻并做流量画像；且 v7 固定 32 位 hex，
     * 与「渠道 {@code out_trade_no} 上限 32 位」零余量（任何前缀/拼接即超限）。
     * 本方法全随机（{@link SecureRandom}），熵 = 4 bit/位，无时间序、无版本位。
     *
     * <p><b>长度选择由调用方按渠道协议给定</b>（协议上限属渠道知识，不在此处固化）；
     * 本方法只保证「长度可控 + 全随机 + 小写 hex」。
     *
     * @param length 期望长度（位，8–64）；20 位 = 80 bit 熵，碰撞概率可忽略
     * @return 指定长度的小写 hex 随机串
     * @throws IllegalArgumentException 长度越界
     */
    public static String randomHex(int length) {
        if (length < MIN_RANDOM_HEX_LEN || length > TOKEN_HEX_LEN) {
            throw new IllegalArgumentException("随机 hex 长度必须在 " + MIN_RANDOM_HEX_LEN
                    + "~" + TOKEN_HEX_LEN + " 之间: " + length);
        }
        byte[] bytes = new byte[(length + 1) / 2];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes).substring(0, length);
    }

    // ==================== 编解码唯一出口（ADR-062 §决策 1） ====================

    /**
     * <b>出方向（toWire）</b>：{@code BINARY(16)} 原始字节 → 线格式 32 位小写 hex。
     *
     * <p>与 {@link #toHexString(byte[])} 同实现，独立命名是为了让「线格式出口」在
     * 调用点<b>可检索</b>：全仓业务标识字符串只许由本方法产出（ADR-062 §决策 1
     * 「唯一一处实现」），将来若要给 id 值加类型前缀，只改这里。
     *
     * @param bytes 数据库读出的 16 字节；{@code null} → {@code null}
     * @return 32 位小写 hex 线格式
     * @throws IllegalArgumentException 长度不为 16 字节
     */
    public static String toWire(byte[] bytes) {
        return toHexString(bytes);
    }

    /**
     * <b>入方向（parseWire）</b>：线格式字符串 → {@code BINARY(16)} 原始字节（严格形态）。
     *
     * <p>与 {@link #toBytes(String)} 同实现；<b>解析方向只许在服务内</b>（仓储/领域），
     * 适配层入参一律先过 {@link #requireWire(String, String)} 形态校验。
     *
     * @param wire 32 位 hex 或 36 位连字符 UUID
     * @return 16 字节
     * @throws IllegalArgumentException 长度或字符不符合 hex 契约
     */
    public static byte[] parseWire(String wire) {
        return toBytes(wire);
    }

    /**
     * <b>线格式形态是否合法</b>（不抛异常）：32 位 hex 或 36 位连字符 UUID（大小写均可）。
     *
     * @param wire 待判定字符串；{@code null}/空白 → {@code false}
     */
    public static boolean isWire(String wire) {
        return wire != null && !wire.isBlank() && toBytesOrNull(wire.strip()) != null;
    }

    /**
     * <b>适配层入参形态校验唯一口</b>（ADR-062 §决策 7.1）——外部路径变量 / 查询参数 /
     * 请求体字段的业务键一律经此校验：合法返回<b>归一化</b>的 32 位小写 hex（统一大小写与
     * 连字符形态，保证同一 id 在库内/线格式只有一种写法），非法直接
     * {@code PARAM_ERROR(1101) → HTTP 400} <b>fail-fast</b>。
     *
     * <p><b>为何不返回 {@code null}</b>：非法形态若落成「查不到」，客户端拼错一个 hex 字符
     * 得到的是 404/空列表而非 400，排查成本被完全转嫁给调用方（ADR-062 §决策 7）。
     *
     * @param field 字段/变量名（进错误消息，便于定位是哪个入参）
     * @param wire  入参原值
     * @return 归一化 32 位小写 hex
     * @throws BadRequestException 形态非法（HTTP 400 / {@code PARAM_ERROR}）
     */
    public static String requireWire(String field, String wire) {
        if (wire == null || wire.isBlank()) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR, field + " 不能为空（应为 32 位 hex 业务键）");
        }
        try {
            return toHexString(toBytes(wire.strip()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR,
                    field + " 必须为 32 位 hex 业务键（或 36 位连字符 UUID）: " + preview(wire));
        }
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
