package org.icedamericanomall.merchant.infrastructure.persistence.shop;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * {@code shop_decoration} 表 Mapper —— 入参/出参均为 PO。
 */
@Mapper
public interface ShopDecorationMapper extends BaseMapper<ShopDecorationPO> {

    /**
     * 保存装修（UPSERT）—— 依赖库表唯一索引 {@code uk_shop(shop_id)}：
     * 首次保存插入、再次保存整体替换；单语句原子写入保证并发首次保存的幂等
     * （选型见 {@code ShopDecorationRepository#save}，ADR-048）。
     *
     * <p>公告 / 两个 JSON 列均为整体替换（PUT 全量语义，发送空列表即清空）；
     * 审计列由 SQL 内 {@code NOW()} 维护，不依赖字段填充器。
     *
     * @param dataObject PO（{@code id} 不参与写入）
     * @return 受影响行数（插入 1；更新 2，值无变化时 0）
     */
    @Update("""
            INSERT INTO shop_decoration
                (shop_id, announcement, banners, hot_spu_ids, create_time, update_time)
            VALUES
                (#{shopId}, #{announcement}, #{banners}, #{hotSpuIds}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                announcement    = VALUES(announcement),
                banners         = VALUES(banners),
                hot_spu_ids = VALUES(hot_spu_ids),
                update_time     = NOW()
            """)
    int upsert(ShopDecorationPO dataObject);
}
