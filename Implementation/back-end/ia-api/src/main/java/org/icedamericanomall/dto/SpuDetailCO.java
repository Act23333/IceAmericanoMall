package org.icedamericanomall.dto;

import java.io.Serializable;

/**
 * 商品详情 — Java Record。映射 item-service {@code SpuVO}（子集）。
 *
 * <p><b>两个约束必须同批（契约 §1.8④ + §4.14.5.2 I70）</b>：
 * <ul>
 *   <li>{@code spuName}（线上键 {@code spu_name}）—— 与 {@code SpuVO.spuName}
 *       <b>同批改名</b>：Jackson {@code SNAKE_CASE} 会忽略未知键，一侧改名另一侧不改，
 *       Feign 解码只会静默落 {@code null}（不抛错），故两端必须在同一 PR 内改完；</li>
 *   <li>承载层 —— 服务间调用走 {@code /internal/item/spu/{spuId}}（I70）返回<b>裸本对象</b>，
 *       不再包 {@code Result} 信封（ADR-004 / 契约 §1.3）。</li>
 * </ul>
 *
 * @param id          资源行主键（技术键，仅渲染/定位用；ADR-058 §1 例外登记）
 * @param spuId       SPU 业务键（32-hex，{@code spu.spu_id}；契约 §1.8①）
 * @param spuName     SPU 名（线上键 {@code spu_name}，契约 §1.8④）
 * @param description 描述
 * @param brand       品牌
 * @param categoryId  类目 id
 * @param mainImage   主图
 * @param images      图集（JSON 文本）
 * @param attributes  属性参数（键值对文本）
 * @param soldCount   销量
 * @param status      状态（1 上架 / 2 下架 / 3 删除）
 */
public record SpuDetailCO(
        Long id,
        String spuId,
        String spuName,
        String description,
        String brand,
        Long categoryId,
        String mainImage,
        String images,
        String attributes,
        Integer soldCount,
        Integer status
) implements Serializable {}
