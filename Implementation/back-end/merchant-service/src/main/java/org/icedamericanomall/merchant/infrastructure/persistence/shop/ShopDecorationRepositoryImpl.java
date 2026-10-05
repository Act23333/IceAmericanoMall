package org.icedamericanomall.merchant.infrastructure.persistence.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.merchant.domain.model.shop.ShopDecoration;
import org.icedamericanomall.merchant.domain.repository.ShopDecorationRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 店铺装修仓储实现（基础设施层）。
 *
 * <p>JSON 字符串 ↔ List 互转在本类收口（静态 {@link ObjectMapper}，不引入 TypeHandler）；
 * 读路径经领域工厂重建（复用校验/归一化，写入时已保证合法）；
 * 写路径为 uk_shop 单语句 UPSERT（见 {@link ShopDecorationMapper#upsert}）。
 */
@Repository
@RequiredArgsConstructor
public class ShopDecorationRepositoryImpl implements ShopDecorationRepository {

    /** 仅供 JSON 字符串列互转（独立实例，不挂全局 Long→String 定制） */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
    };

    private final ShopDecorationMapper shopDecorationMapper;

    @Override
    public Optional<ShopDecoration> findByShopId(Long shopId) {
        if (shopId == null) {
            return Optional.empty();
        }
        ShopDecorationPO dataObject = shopDecorationMapper.selectOne(
                new LambdaQueryWrapper<ShopDecorationPO>()
                        .eq(ShopDecorationPO::getShopId, shopId));
        return Optional.ofNullable(toDomain(dataObject));
    }

    @Override
    public void save(ShopDecoration decoration) {
        shopDecorationMapper.upsert(toPO(decoration));
    }

    // ==================== PO ↔ 领域对象（内联转换） ====================

    private static ShopDecorationPO toPO(ShopDecoration decoration) {
        ShopDecorationPO dataObject = new ShopDecorationPO();
        dataObject.setShopId(decoration.getShopId());
        dataObject.setAnnouncement(decoration.getAnnouncement());
        dataObject.setBanners(toJson(decoration.getBanners()));
        dataObject.setHotProductIds(toJson(decoration.getHotProductIds()));
        return dataObject;
    }

    private static ShopDecoration toDomain(ShopDecorationPO dataObject) {
        if (dataObject == null) {
            return null;
        }
        return ShopDecoration.of(dataObject.getShopId(), dataObject.getAnnouncement(),
                parse(dataObject.getBanners(), STRING_LIST, "轮播图数据解析失败"),
                parse(dataObject.getHotProductIds(), LONG_LIST, "推荐商品位数据解析失败"));
    }

    // ==================== JSON 字符串列互转（收口） ====================

    /** List → JSON 字符串；空列表落 NULL（清空语义）。 */
    private static String toJson(List<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return JSON_MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "装修数据序列化失败");
        }
    }

    /** JSON 字符串 → List；空列返回空数组。 */
    private static <T> List<T> parse(String json, TypeReference<List<T>> type, String errorMessage) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON_MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, errorMessage);
        }
    }
}
