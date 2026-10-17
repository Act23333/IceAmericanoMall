package org.icedamericanomall.item.domain.model.spu;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.utils.IdGenerator;

import java.util.regex.Pattern;

/**
 * 短码（{@code spu.short_code}）—— SPU 的<b>对外寻址别名</b>，不是实体标识（纯 POJO，零框架依赖）。
 *
 * <p><b>定位（硬约束，ADR-063 §决策 8 + 契约 §1.9⑧）</b>：短码只是「把小程序码 {@code scene}
 * 换回 SPU」的钥匙，形态上必须：
 * <ul>
 *   <li><b>短</b>：微信 {@code wxacode.getUnlimited} 的 {@code scene} 上限
 *       {@code WechatMiniApiClient#SCENE_MAX_LENGTH} = 32 可见字符，而
 *       {@code "p=" + 32-hex 业务键} = <b>34 字符（已超限 2）</b> —— 这是短码存在的唯一理由；</li>
 *   <li><b>不可推断</b>：纯 {@link IdGenerator#randomHex(int)}，<b>无时间序</b>、
 *       <b>不复用业务键任何子串</b>（不截取、不加前后缀），故与 {@code spu_id} 无派生关系；</li>
 *   <li><b>不可当标识用</b>：不得用于任何业务判断（判重 / 判归属 / 判权限 / 判「是否同一商品」），
 *       不得替代 {@code spu_id}，<b>不得进入任何 VO / DTO</b>（进报文即把别名升格为标识）。</li>
 * </ul>
 *
 * <p><b>生命周期</b>：与商品同生命周期 —— 创建时定稿、<b>永不变更、永不回收</b>
 * （软删 {@code status=3} 时保留占位）。不给它设过期：已印在包装/海报上的码无法撤回，
 * 过期只会造成一次静默断链。
 *
 * <p><b>字符集为何限定小写 hex</b>：{@code spu} 表排序规则是 {@code utf8mb4_unicode_ci}
 * （<b>大小写不敏感</b>）—— 只有单一大小写形态才不会被 CI 折叠成「看似不同、实为同一码」。
 * 将来若换 base62，<b>必须</b>同批把该列改 {@code _bin} / {@code _as_cs}，否则
 * {@code AbC} 与 {@code abc} 会被唯一键判等（静默冲突）。
 */
public final class ShortCode {

    /** 当前实现对外的短码长度（10 位 hex = 40 bit 熵）。 */
    public static final int LENGTH = 10;

    /**
     * 形态下限（8 位 hex = 32 bit）。
     *
     * <p>契约写「8–10 位」以容纳历史取值；8 位在 10⁵ 商品规模下期望碰撞 ≈ 1.2 次，
     * 故实现取 10 位（同规模 ≈ 0.0045 次）。
     */
    public static final int MIN_LENGTH = 8;

    /** 形态上限（10 位 hex）；列宽 {@code VARCHAR(16)} 为将来升到 16 位留位。 */
    public static final int MAX_LENGTH = 10;

    /** 线格式形态：8–10 位<b>小写</b> hex（大写一律 400，见类注释的 CI 折叠说明）。 */
    private static final Pattern WIRE_FORM = Pattern.compile("^[0-9a-f]{" + MIN_LENGTH + "," + MAX_LENGTH + "}$");

    private ShortCode() {
    }

    /**
     * 生成新短码（发号层唯一出口）：{@link IdGenerator#randomHex(int)} 的 10 位纯随机小写 hex。
     *
     * <p><b>只能在商品创建的事务内调用一次</b>（{@code Spu#publish}）—— 「一次生成、永不变更」；
     * 冲突重试也只在发布之前发生（此时尚无任何物料引用它，换号无外部可见后果）。
     */
    public static String newCode() {
        return IdGenerator.randomHex(LENGTH);
    }

    /**
     * 形态是否合法（不抛异常）—— 供读侧 {@code fail-open} 过滤与仓储防御使用。
     *
     * @param code 待判定短码；{@code null}/空白 → {@code false}
     */
    public static boolean isValid(String code) {
        return code != null && WIRE_FORM.matcher(code).matches();
    }

    /**
     * 适配层入参形态校验（唯一入口）：合法返回去空白的原值，非法即
     * {@code PARAM_ERROR(1101) → HTTP 400} <b>fail-fast</b>。
     *
     * <p><b>为何不返回 null 也不回落到「查不到」</b>：短码是<b>印在物料上被手抄/OCR</b> 的标识，
     * 拼错必须得到「你的参数不对」而不是「这个商品不存在」—— 后者会让落地页把一个
     * 拼写问题渲染成「商品已下架/已删除」，与真实业务态混为一谈（契约 §4.4.1 失败语义表）。
     *
     * @param code 路径变量原文
     * @return 通过校验的短码（已 strip）
     * @throws BadRequestException 形态非法（HTTP 400 / {@code PARAM_ERROR}）
     */
    public static String require(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR,
                    "短码不能为空（应为 " + MIN_LENGTH + "–" + MAX_LENGTH + " 位小写 hex）");
        }
        String normalized = code.strip();
        if (!isValid(normalized)) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR,
                    "短码形态非法（应为 " + MIN_LENGTH + "–" + MAX_LENGTH + " 位小写 hex）: "
                            + preview(normalized));
        }
        return normalized;
    }

    /** 异常消息中的入参预览（截断，防止异常日志被超长入参刷屏）。 */
    private static String preview(String code) {
        return code.length() <= MAX_LENGTH ? code : code.substring(0, MAX_LENGTH) + "...";
    }
}
