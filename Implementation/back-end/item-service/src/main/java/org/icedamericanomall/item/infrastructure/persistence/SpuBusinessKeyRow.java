package org.icedamericanomall.item.infrastructure.persistence;

import lombok.Data;

/**
 * SPU 业务键 → 技术键的投影行（{@code SpuMapper.selectIdByBusinessIds} 专用）。
 *
 * <p>只承载两列（{@code id} 技术主键 + {@code spu_id} 业务键 BINARY(16)，ADR-050/061），
 * 不落整行 {@link SpuPO} —— 解析调用只要键，不需要商品全字段。
 */
@Data
public class SpuBusinessKeyRow {

    /** 技术主键（{@code spu.id}）。 */
    private Long id;

    /** 业务键原始字节（{@code spu.spu_id BINARY(16)}；出持久化边界转 32-hex）。 */
    private byte[] spuId;
}
