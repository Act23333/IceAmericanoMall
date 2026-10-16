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
 * <p>cart 表不存商品快照：{@code spu_name}/规格/图片/单价由编排层读模型批量回填，
 * 小计（subTotal）与金额同样由编排层计算，故此处显式忽略（见下方各 {@code @Mapping}）。
 *
 * <p><b>同名异义必须显式 ignore（ADR-058 §4）</b>：{@code CartItem.skuId} 是
 * <b>技术外键</b>（{@code cart.sku_id BIGINT}），而 {@code CartItemVO.skuId} /
 * {@code CartItemCO.skuId} 是 <b>SKU 业务键</b>（32-hex）—— 二者同名异义；
 * MapStruct 会把 {@code Long 1000} 静默转成字符串 {@code "1000"}（看似有值、最难排查），
 * 故必须 ignore 并由 {@code CartManager} 经读模型回填。
 *
 * <p><b>构建期守卫</b>：本仓 MapStruct 未映射目标属性为<b>构建失败</b>
 * （parent pom {@code -Amapstruct.unmappedTargetPolicy=ERROR}）。因此每一处
 * {@code ignore = true} 都是「有意不映射」的显式声明，不得为了通过编译而放宽全局策略。
 */
@Mapper(componentModel = "spring")
public interface CartConverter {

    /**
     * 聚合 → 购物车项 VO。
     *
     * <p>{@code skuId}（业务键）/ 商品名 / 规格 / 图片 / 单价在
     * {@code CartManager.getCartView} 读时批量回填，小计（price × quantity，分）由同一处计算；
     * 此处留空即在线为空（fail-open 口径，ADR-057 + ADR-061 §决策 3：不回落技术键）。
     */
    @Mapping(target = "skuId", ignore = true)
    @Mapping(target = "spuId", ignore = true)
    @Mapping(target = "spuName", ignore = true)
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
     * <p>{@code skuId}（业务键）由 {@code CartManager.getSelectedItemCos} 经同库只读读模型回填
     * （{@code cart.sku_id} 技术键 → {@code sku.sku_id} 业务键），故此处 ignore ——
     * 绝不把技术键自动转成字符串（那会让 trade 侧按业务键查 SKU 永远查不到）。
     */
    @Mapping(target = "cartItemId", source = "id")
    @Mapping(target = "skuId", ignore = true)
    @Mapping(target = "spuId", ignore = true)
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "spec", ignore = true)
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "price", ignore = true)
    CartItemCO itemToCO(CartItem item);

    List<CartItemCO> itemsToCOs(List<CartItem> items);
}
