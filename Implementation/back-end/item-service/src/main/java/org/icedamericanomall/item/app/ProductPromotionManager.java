package org.icedamericanomall.item.app;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.PromotionClient;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.dto.PromotionQueryCO;
import org.icedamericanomall.item.adapter.response.PromotionTagVO;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 商品详情促销标签编排（F10，契约 §4.4.x P2 V5.11 增量 {@code promotions}，ADR-025 用例族拆分）——
 * 复用 I44 生效促销查询（{@code PromotionClient}），把店铺「进行中」满减/满折活动装配为
 * {@link PromotionTagVO} 展示标签，数据源与下单计价同源（promotion 表）——用户所见即所得。
 *
 * <p><b>短 TTL 本地缓存（ADR-041 先例，对齐 trade {@code FreightPostageResolver}）</b>：
 * 商品详情是高频读端点，每次详情一次跨服务查询会把 marketing-service 打成读热点；
 * 现以 Caffeine 按 sellerId 缓存标签集合——促销分钟级生效可接受（契约 §4.14.8b V5.11 注记），
 * 无需事件式失效。
 *
 * <p><b>降级语义：fail-open（与 trade 计价刻意不同）</b>：I44 的 {@code PromotionClient} 为
 * fail-closed（抛 {@code BizException}，ADR-011 判别型查询语义不变）；但本消费方是<b>展示场景</b>，
 * 标签缺失只损失促销曝光、不产生资损，故由本类 loader 捕获降级异常返回空列表——
 * 详情页绝不因促销查询失败而失败（契约 §4.14.8b「调用方扩展（V5.11）」注记口径）。
 * catch 后返回空列表会随 Caffeine 正常缓存 60s：marketing 抖动时 60s 内不再重试打压，
 * 恢复后至多延迟一个 TTL 可见——对促销标签展示可接受。
 */
@Slf4j
@Component
public class ProductPromotionManager {

    /** 缓存条目上限（商家数量级）—— 防内存失控；超出按 LRU 淘汰，仅损失一次缓存命中。 */
    private static final long CACHE_MAXIMUM_SIZE = 10_000L;

    /** 写后过期 60 秒 —— 促销分钟级生效即可接受（ADR-041 先例口径），无需更短 TTL 或主动失效。 */
    private static final Duration CACHE_EXPIRE_AFTER_WRITE = Duration.ofSeconds(60);

    /** I44 契约活动类型：1=满减（PromotionCO.type）。 */
    private static final Integer PROMO_TYPE_FULL_REDUCE = 1;
    /** I44 契约活动类型：2=满折（PromotionCO.type）。 */
    private static final Integer PROMO_TYPE_LIMIT_DISCOUNT = 2;
    /** 展示标签类型：满减（契约 §4.4.x P2 字符串口径）。 */
    private static final String TAG_TYPE_FULL_REDUCE = "FULL_REDUCE";
    /** 展示标签类型：满折（契约 §4.4.x P2 字符串口径）。 */
    private static final String TAG_TYPE_LIMIT_DISCOUNT = "LIMIT_DISCOUNT";

    private final PromotionClient promotionClient;
    private final LoadingCache<Long, List<PromotionTagVO>> tagCache;

    public ProductPromotionManager(PromotionClient promotionClient) {
        this.promotionClient = promotionClient;
        this.tagCache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAXIMUM_SIZE)
                .expireAfterWrite(CACHE_EXPIRE_AFTER_WRITE)
                .build(this::loadTags);
    }

    /**
     * 取店铺进行中促销展示标签（短 TTL 缓存，fail-open）。
     *
     * @param sellerId 商家 user_id；为空（商品无归属场景）返回空列表
     * @return 进行中满减/满折标签；无活动/marketing 不可用 → 空列表，不报错
     */
    public List<PromotionTagVO> tagsFor(Long sellerId) {
        if (sellerId == null) {
            return List.of();
        }
        List<PromotionTagVO> tags = tagCache.get(sellerId);
        return tags == null ? List.of() : tags;
    }

    /**
     * 缓存加载：I44 按 sellerId 取该店铺生效促销集合并映射为展示标签。
     * <p>展示场景 fail-open：catch 降级异常返回空列表（会随 Caffeine 缓存 60s，
     * 抖动期间不再重试打压 marketing），绝不向详情页上抛。
     */
    private List<PromotionTagVO> loadTags(Long sellerId) {
        try {
            List<PromotionCO> promotions = promotionClient.queryActive(new PromotionQueryCO(sellerId));
            List<PromotionTagVO> tags = (promotions == null ? List.<PromotionCO>of() : promotions).stream()
                    .filter(this::isDisplayable)
                    .map(this::toTag)
                    .toList();
            log.debug("促销标签加载: sellerId={}, tags={}", sellerId, tags.size());
            return tags;
        } catch (Exception e) {
            log.warn("促销标签查询失败，详情页促销位返回空（fail-open）: sellerId={}, cause={}",
                    sellerId, e.getMessage());
            return List.of();
        }
    }

    /** 仅装配契约内类型（1/2）；契约外数据防御性过滤，不出空类型标签。 */
    private boolean isDisplayable(PromotionCO promotion) {
        return promotion != null
                && (PROMO_TYPE_FULL_REDUCE.equals(promotion.getType())
                || PROMO_TYPE_LIMIT_DISCOUNT.equals(promotion.getType()));
    }

    /** I44 促销 → 展示标签（契约字段逐字映射；endTime V5.11 起 I44 透出）。 */
    private PromotionTagVO toTag(PromotionCO promotion) {
        PromotionTagVO tag = new PromotionTagVO();
        tag.setType(PROMO_TYPE_FULL_REDUCE.equals(promotion.getType())
                ? TAG_TYPE_FULL_REDUCE : TAG_TYPE_LIMIT_DISCOUNT);
        tag.setName(promotion.getName());
        tag.setThreshold(promotion.getThreshold());
        tag.setDiscountValue(promotion.getDiscountValue());
        tag.setMaxDiscount(promotion.getMaxDiscount());
        tag.setEndTime(promotion.getEndTime());
        return tag;
    }
}
