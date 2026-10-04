package org.icedamericanomall.cart.adapter.assembler;

import org.icedamericanomall.cart.adapter.response.CartItemVO;
import org.icedamericanomall.cart.domain.model.cart.CartItem;
import org.icedamericanomall.dto.CartItemCO;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 购物车转换器 —— 聚合 → VO / 内部 CO。
 *
 * <p>cart 表不存商品快照：商品名/规格/单价/图片由编排层批量回填，
 * 小计（subTotal）与金额同样由编排层计算，故此处忽略。
 */
@Mapper(componentModel = "spring")
public interface CartConverter {

    CartItemVO itemToVO(CartItem item);

    List<CartItemVO> itemsToVOs(List<CartItem> items);

    /** 聚合 → 内部下单 CO（商品快照字段由调用方 item-service 侧补齐，此处恒空）。 */
    CartItemCO itemToCO(CartItem item);

    List<CartItemCO> itemsToCOs(List<CartItem> items);
}
