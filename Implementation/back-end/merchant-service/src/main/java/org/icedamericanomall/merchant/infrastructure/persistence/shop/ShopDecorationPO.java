package org.icedamericanomall.merchant.infrastructure.persistence.shop;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * {@code shop_decoration} 表映射对象（PO）—— 仅存在于基础设施持久化层，不外泄。
 *
 * <p>唯一索引 {@code uk_shop(shop_id)}（装修与 shop 1:1）；
 * {@code banners} / {@code hot_spu_ids} 为 JSON 数组字符串列（不引入 TypeHandler），
 * 序列化在 {@link ShopDecorationRepositoryImpl} 收口。
 */
@Data
@TableName("shop_decoration")
public class ShopDecorationPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺 ID（业务唯一键 uk_shop，与 shop 1:1） */
    private Long shopId;

    /** 店铺公告（≤200 字） */
    private String announcement;

    /** 轮播图 JSON 数组字符串（≤5 个 http(s) URL） */
    private String banners;

    /** 推荐商品位 JSON 数组字符串（≤8 个 SPU <b>业务键</b> 32-hex，去重；ADR-062 §决策 3） */
    private String hotSpuIds;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
