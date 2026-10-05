package org.icedamericanomall.merchant.domain.model.shop;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

import java.util.List;
import java.util.Objects;

/**
 * 店铺装修（与 shop 1:1 的聚合，ADR-048）—— 公告 + 轮播图（≤5）+ 推荐商品位（≤8）。
 *
 * <p>存储形态：banners / hotProductIds 以 JSON 字符串列持久化（不引入 TypeHandler），
 * JSON ↔ List 互转收口在仓储实现；领域层只认 List，并在本类集中校验
 * 条数 / URL 格式 / 去重（创建即校验 + 归一化）。
 *
 * <p>Phase 1 取舍（ADR-048）：{@code hotProductIds} 不校验商品归属（跨服务校验留 Phase 2），
 * 展示端（店铺页商品卡）按需过滤失效 / 非在售 id。
 */
@Data
@Builder
public class ShopDecoration {

    /** 轮播图上限 */
    public static final int MAX_BANNERS = 5;

    /** 推荐商品位上限 */
    public static final int MAX_HOT_PRODUCT_IDS = 8;

    /** 公告长度上限（库表 VARCHAR(200)） */
    public static final int MAX_ANNOUNCEMENT_LENGTH = 200;

    private static final String HTTP_PREFIX = "http://";
    private static final String HTTPS_PREFIX = "https://";

    /** 店铺 ID（业务唯一键 uk_shop，与 shop 1:1） */
    private Long shopId;

    /** 店铺公告（空白归一化为 null） */
    private String announcement;

    /** 轮播图 URL（≤5，每条须为 http(s) 链接） */
    private List<String> banners;

    /** 推荐商品位（product.id，≤8，去重保序） */
    private List<Long> hotProductIds;

    // ==================== 工厂 ====================

    /**
     * 创建装修（创建即校验 + 归一化：公告去首尾空白、轮播逐条去空白并校验 URL、
     * 推荐位去重保序）。
     *
     * @throws BizException shopId 缺失（PARAM_ERROR）、公告超长（PARAM_ERROR）、
     *                      轮播超 5 张或含非 http(s) 链接（PARAM_ERROR）、
     *                      去重后推荐位超 8 个（PARAM_ERROR）
     */
    public static ShopDecoration of(Long shopId, String announcement,
                                    List<String> banners, List<Long> hotProductIds) {
        if (shopId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "店铺 ID 不能为空");
        }
        return ShopDecoration.builder()
                .shopId(shopId)
                .announcement(normalizeAnnouncement(announcement))
                .banners(normalizeBanners(banners))
                .hotProductIds(normalizeHotProductIds(hotProductIds))
                .build();
    }

    /** 未装修占位（S1c 空对象：公告为空、两个列表为空数组）。 */
    public static ShopDecoration blank() {
        return ShopDecoration.builder()
                .banners(List.of())
                .hotProductIds(List.of())
                .build();
    }

    // ==================== 内部校验 / 归一化 ====================

    private static String normalizeAnnouncement(String announcement) {
        if (announcement == null || announcement.isBlank()) {
            return null;
        }
        String trimmed = announcement.strip();
        if (trimmed.length() > MAX_ANNOUNCEMENT_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "公告最多 " + MAX_ANNOUNCEMENT_LENGTH + " 字");
        }
        return trimmed;
    }

    private static List<String> normalizeBanners(List<String> banners) {
        if (banners == null || banners.isEmpty()) {
            return List.of();
        }
        if (banners.size() > MAX_BANNERS) {
            throw new BizException(ErrorCode.PARAM_ERROR, "轮播图最多 " + MAX_BANNERS + " 张");
        }
        return banners.stream()
                .map(String::strip)
                .peek(ShopDecoration::assertHttpUrl)
                .toList();
    }

    private static void assertHttpUrl(String url) {
        if (url.isEmpty() || !(url.startsWith(HTTP_PREFIX) || url.startsWith(HTTPS_PREFIX))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "轮播图地址必须为 http(s) 链接");
        }
    }

    private static List<Long> normalizeHotProductIds(List<Long> hotProductIds) {
        if (hotProductIds == null || hotProductIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = hotProductIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinct.size() > MAX_HOT_PRODUCT_IDS) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "推荐商品位最多 " + MAX_HOT_PRODUCT_IDS + " 个");
        }
        return distinct;
    }
}
