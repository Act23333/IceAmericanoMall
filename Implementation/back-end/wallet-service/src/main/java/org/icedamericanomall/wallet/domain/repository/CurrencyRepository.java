package org.icedamericanomall.wallet.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.wallet.domain.model.currency.Currency;
import org.icedamericanomall.wallet.domain.model.currency.ExchangeRate;

import java.util.List;

/**
 * 币种/汇率仓储端口（领域层）—— 实现落基础设施层 {@code infrastructure/persistence/currency}。
 *
 * <p>全部按<b>业务唯一键 {@code code}</b> 操作（禁止按技术主键 id）；读侧返回聚合
 * {@link Currency}（汇率以 1:1 值组件挂载，未配置为 null）。IPage 参数与返回沿用
 * {@code TaskDefRepository} 分页先例（分页语义由基础设施层承接）。
 */
public interface CurrencyRepository {

    /**
     * 启用币种 + 汇率联查（P36 数据源；仅 {@code status=1}，code 升序）。
     *
     * @return 启用币种列表（含汇率值组件，未配置汇率的币种 rate 为 null）
     */
    List<Currency> listEnabledWithRate();

    /**
     * 按业务唯一键查币种（含汇率；I61 / 管理端写操作的存在性校验数据源）。
     *
     * @param code 币种代码（ISO 4217）
     * @return 币种聚合（未配置汇率时 rate 为 null）；不存在返回 null
     */
    Currency findByCode(String code);

    /**
     * 币种 + 汇率联合分页（AD66 数据面，含 source/effectTime 供 stale 告警与人工兜底）。
     *
     * @param status   状态过滤（null=全部）
     * @param pageNum  页码（1 起）
     * @param pageSize 页大小
     * @return 币种分页（聚合形态，应用层转换为管理端 VO）
     */
    IPage<Currency> pageWithRate(Integer status, int pageNum, int pageSize);

    /**
     * upsert 汇率（一行一币；按 {@code code} 命中更新、未命中插入——{@code uk_code} 兜底）。
     *
     * <p>更新采用显式 SET（含 updaterId/remark 置 null），保证 MANUAL → API 切换时留痕字段
     * 同步归位，不残留上次修正人。
     *
     * @param rate 汇率值对象（业务键 code 定位）
     */
    void updateRate(ExchangeRate rate);

    /**
     * 启用/停用币种（AD68；条件更新，按 {@code code} 命中才生效）。
     *
     * @param code   币种代码
     * @param status 目标状态（1=启用 0=停用）
     * @return 命中返回 {@code true}；币种不存在返回 {@code false}（应用层据此抛 1103）
     */
    boolean updateStatus(String code, int status);
}
