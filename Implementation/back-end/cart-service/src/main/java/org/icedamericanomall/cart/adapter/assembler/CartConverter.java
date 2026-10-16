package org.icedamericanomall.cart.adapter.assembler;

import org.icedamericanomall.cart.adapter.response.CartItemVO;
import org.icedamericanomall.cart.domain.model.cart.CartItem;
import org.icedamericanomall.dto.CartItemCO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 购物车转换器 —— 聚合 → VO / 内部 CO。
 *
 * <p>cart 表不存商品快照：商品名/规格/单价/图片由编排层批量回填，
 * 小计（subTotal）与金额同样由编排层计算，故此处显式忽略（见下方各 {@code @Mapping}）。
 *
 * <p><b>构建期守卫</b>：本仓 MapStruct 未映射目标属性为<b>构建失败</b>
 * （parent pom {@code -Amapstruct.unmappedTargetPolicy=ERROR}）。因此每一处
 * {@code ignore = true} 都是「有意不映射」的显式声明，不得为了通过编译而放宽全局策略；
 * 反之，新增字段若既未映射又未声明 ignore，构建会直接失败而不再只是警告。
 */
@Mapper(componentModel = "spring")
public interface CartConverter {

    /**
     * 聚合 → 购物车项 VO。
     *
     * <p>商品名/规格/图片/单价在 {@code CartManager.getCartView} 读时经 I13 批量回填，
     * 小计（price × quantity，分）由同一处计算；此处留空即在线为空（fail-open 口径，ADR-057）。
     */
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "spec", ignore = true)
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "subTotal", ignore = true)
    CartItemVO itemToVO(CartItem item);

    List<CartItemVO> itemsToVOs(List<CartItem> items);

    /**
     * 聚合 → 内部下单 CO（I14）。
     *
     * <p>{@code cartItemId} 必须显式映射：源字段名为 {@code id}、目标名为 {@code cartItemId}，
     * 名字不同无法自动映射 —— 缺失时恒 null，下单后「删除已购购物车项」会静默跳过，购物车永不清除。
     *
     * <p>商品快照字段（productId/productName/spec/image/price）由调用方 trade-service
     * 从 I13 的 {@code SkuCO} 补齐（下单快照以商品域为准，避免购物车侧快照腐化），此处恒空。
     */
    @Mapping(target = "cartItemId", source = "id")
    @Mapping(target = "productId", ignore = true)
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "spec", ignore = true)
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "price", ignore = true)
    CartItemCO itemToCO(CartItem item);

    List<CartItemCO> itemsToCOs(List<CartItem> items);
}
