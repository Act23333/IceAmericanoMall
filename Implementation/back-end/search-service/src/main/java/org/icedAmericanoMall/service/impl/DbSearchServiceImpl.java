package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.convert.ProductSearchConverter;
import org.icedAmericanoMall.domain.entity.ProductEntity;
import org.icedAmericanoMall.domain.entity.SkuEntity;
import org.icedAmericanoMall.domain.vo.ProductSearchVO;
import org.icedAmericanoMall.mapper.ProductMapper;
import org.icedAmericanoMall.mapper.SkuMapper;
import org.icedAmericanoMall.service.SearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DB LIKE 搜索 — ES 未启用时的默认实现。
 * 使用 MyBatis-Plus lambdaQuery 参数化，杜绝 SQL 注入。
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class DbSearchServiceImpl extends ServiceImpl<ProductMapper, ProductEntity> implements SearchService {

    private final SkuMapper skuMapper;
    private final ProductSearchConverter converter;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String HOT_KEYWORDS_KEY = "search:hot:keywords";
    private static final String HISTORY_KEY_PREFIX = "search:history:";

    @Override
    public Page<ProductSearchVO> search(String keyword, Long categoryId, int page, int size) {
        if (keyword != null && !keyword.isBlank()) {
            recordKeyword(keyword.trim());
        }

        LambdaQueryWrapper<ProductEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductEntity::getStatus, 1);

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(ProductEntity::getName, keyword)
                    .or()
                    .like(ProductEntity::getDescription, keyword));
        }
        if (categoryId != null) {
            wrapper.eq(ProductEntity::getCategoryId, categoryId);
        }
        wrapper.orderByDesc(ProductEntity::getSoldCount);

        Page<ProductEntity> entityPage = page(new Page<>(page, size), wrapper);

        if (entityPage.getRecords().isEmpty()) {
            return new Page<>(page, size, 0);
        }

        Map<Long, Integer> priceMap = getMinPrices(entityPage.getRecords());
        List<ProductSearchVO> vos = converter.entitiesToVOs(entityPage.getRecords());
        vos.forEach(vo -> vo.setPrice(priceMap.getOrDefault(vo.getId(), 0)));

        Page<ProductSearchVO> voPage = new Page<>(page, size, entityPage.getTotal());
        voPage.setRecords(vos);
        return voPage;
    }

    @Override
    public List<String> getHotKeywords(int limit) {
        var top = redisTemplate.opsForZSet().reverseRange(HOT_KEYWORDS_KEY, 0, limit - 1);
        return top != null ? new ArrayList<>(top) : Collections.emptyList();
    }

    @Override
    public List<String> getSearchHistory(Long userId, int limit) {
        String key = HISTORY_KEY_PREFIX + userId;
        List<String> history = redisTemplate.opsForList().range(key, 0, limit - 1);
        return history != null ? history : Collections.emptyList();
    }

    @Override
    public List<ProductSearchVO> vectorSearch(double[] embedding, int size) {
        // DB 不支持向量搜索，返回空列表（降级处理）
        return Collections.emptyList();
    }

    private void recordKeyword(String keyword) {
        try {
            redisTemplate.opsForZSet().incrementScore(HOT_KEYWORDS_KEY, keyword, 1);
        } catch (Exception ignored) {
        }
    }

    private Map<Long, Integer> getMinPrices(List<ProductEntity> products) {
        if (products.isEmpty()) return Collections.emptyMap();
        List<Long> ids = products.stream().map(ProductEntity::getId).toList();

        List<SkuEntity> skus = skuMapper.selectList(
                new LambdaQueryWrapper<SkuEntity>()
                        .in(SkuEntity::getProductId, ids)
                        .eq(SkuEntity::getStatus, 1));

        return skus.stream()
                .collect(Collectors.groupingBy(
                        SkuEntity::getProductId,
                        Collectors.collectingAndThen(
                                Collectors.minBy(Comparator.comparingInt(SkuEntity::getPrice)),
                                opt -> opt.map(SkuEntity::getPrice).orElse(0))));
    }
}
