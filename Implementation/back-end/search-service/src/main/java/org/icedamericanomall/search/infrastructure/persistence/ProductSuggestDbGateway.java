package org.icedamericanomall.search.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.search.domain.gateway.ProductSuggestGateway;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 商品名联想 DB 实现 —— {@code product} 表前缀 LIKE 直查（SE5 源 2，恒走 DB）。
 *
 * <p>MyBatis-Plus {@code likeRight} 生成 {@code name LIKE 'prefix%'}（前缀锚定，
 * 可命中索引前缀；参数化查询，无 SQL 注入面）。排序按销量降序（热门商品优先成为联想词）。
 * 与 SE1 关键词检索同用 {@code status=1} 在售口径（下架商品不进联想）。
 */
@Component
@RequiredArgsConstructor
public class ProductSuggestDbGateway implements ProductSuggestGateway {

    /** 在售状态（item-service 商品域的口径，此处只读镜像表）。 */
    private static final int STATUS_ON_SALE = 1;

    private final ProductMapper productMapper;

    /**
     * 商品名前缀联想 —— 在售商品名称 LIKE 前缀命中，同名去重，销量降序。
     */
    @Override
    public List<String> suggestNames(String prefix, int limit) {
        List<ProductPO> products = productMapper.selectList(
                new LambdaQueryWrapper<ProductPO>()
                        .select(ProductPO::getName)
                        .eq(ProductPO::getStatus, STATUS_ON_SALE)
                        .likeRight(ProductPO::getName, prefix)
                        .orderByDesc(ProductPO::getSoldCount)
                        .last("LIMIT " + limit));
        return products.stream()
                .map(ProductPO::getName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
