package org.icedamericanomall.item.domain.model.home;

import org.icedamericanomall.common.constant.CsvIdLimits;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BadRequestException;
import org.icedamericanomall.common.utils.IdGenerator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 首页装修「商品列表 CSV」的读写口径（{@code home_config.spu_ids}，纯 POJO，零框架依赖）。
 *
 * <p><b>为何要单独一个值对象</b>：{@code spu_ids} 是<b>实体寻址</b>型承载位（装修项要能回链商品详情），
 * 故放的是 <b>SPU 业务键</b>（32-hex）。业务键化之后它不再是「几个数字」而是「每项 33 字符」，
 * 于是三条约束必须同时成立（ADR-063 §决策 7 表下方 + 契约 §1.9⑦-3）：
 * <ol>
 *   <li><b>容量</b>：列宽 {@code VARCHAR(500)} ÷ 33 字符/项 ⇒ <b>≤ {@value CsvIdLimits#MAX_CSV_IDS_IN_COLUMN}</b> 项；</li>
 *   <li><b>形态</b>：每项必须是合法业务键（写侧 fail-fast 400）；</li>
 *   <li><b>去重</b>：重复项归一，不落重复值（同一商品在首屏出现两次是配置错误，不是特性）。</li>
 * </ol>
 *
 * <p><b>读写两侧的失败语义刻意不同（不得混用）</b>：
 * <ul>
 *   <li><b>写侧 fail-closed</b>（{@link #normalizeForWrite(String)}）：配置是管理端手工录入的，
 *       写错就该立刻 400 改掉，而不是把错误留到首屏；</li>
 *   <li><b>读侧 fail-open</b>（{@link #filterForRead(String)}）：历史配置可能含旧技术键或脏值，
 *       <b>个别项非法只丢弃该项</b>，绝不使整条装修配置失效 —— 首屏数据坏一条不得拖垮首页。</li>
 * </ul>
 */
public final class SpuIdCsv {

    /** CSV 分隔符（与库内存储口径一致）。 */
    private static final String SEPARATOR = ",";

    private SpuIdCsv() {
    }

    /**
     * 写侧归一：逐项形态校验（业务键）+ 条数上限 + 去重 + 保持原顺序。
     *
     * @param csv 请求里的商品列表 CSV（可为空：非商品组类型没有商品）
     * @return 归一后的 CSV（空输入 → {@code null}，不落空串）
     * @throws BadRequestException 任一项形态非法（{@code PARAM_ERROR} / 400）或条数超上限
     */
    public static String normalizeForWrite(String csv) {
        List<String> items = split(csv);
        if (items.isEmpty()) {
            return null;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String item : items) {
            // 形态校验唯一出口（ADR-062 §决策 7.1）：拼错一个 hex 字符应得 400，而不是静默丢项
            unique.add(IdGenerator.requireWire("spu_ids", item));
        }
        if (unique.size() > CsvIdLimits.MAX_CSV_IDS_IN_COLUMN) {
            throw new BadRequestException(ErrorCode.PARAM_ERROR,
                    "商品列表最多 " + CsvIdLimits.MAX_CSV_IDS_IN_COLUMN + " 项（列宽 VARCHAR(500) 按 33 字符/项倒推）");
        }
        return String.join(SEPARATOR, unique);
    }

    /**
     * 读侧过滤（<b>fail-open</b>）：丢弃形态非法的项，保留其余；解析失败返回 {@code null}。
     *
     * <p><b>绝不抛异常</b>：读到脏值只降级这一条配置的商品位，首页照常渲染
     * （与 {@code SpuPromotionManager} 的展示型 fail-open 同一纪律）。
     *
     * @param csv 库里的商品列表 CSV
     * @return 仅含合法业务键的 CSV（全部非法 → {@code null}）
     */
    public static String filterForRead(String csv) {
        List<String> valid = new ArrayList<>();
        for (String item : split(csv)) {
            if (IdGenerator.isWire(item)) {
                valid.add(item);
            }
        }
        return valid.isEmpty() ? null : String.join(SEPARATOR, valid);
    }

    /** 切分 CSV：去空白项、去首尾空白；不做形态判断（由调用方按侧决定语义）。 */
    private static List<String> split(String csv) {
        List<String> items = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return items;
        }
        for (String raw : csv.split(SEPARATOR)) {
            String item = raw.strip();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }
}
