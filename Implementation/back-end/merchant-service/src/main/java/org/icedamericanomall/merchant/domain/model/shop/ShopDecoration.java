package org.icedamericanomall.merchant.domain.model.shop;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.utils.IdGenerator;

import java.util.List;
import java.util.Objects;

/**
 * 店铺装修（与 shop 1:1 的聚合，ADR-048）—— 公告 + 轮播图（≤5）+ 推荐商品位（≤8）。
 *
 * <p>存储形态：banners / hotSpuIds 以 JSON 字符串列持久化（不引入 TypeHandler），
 * JSON ↔ List 互转收口在仓储实现；领域层只认 List，并在本类集中校验
 * 条数 / URL 格式 / 业务键形态 / 去重（创建即校验 + 归一化）。
 *
 * <p><b>推荐商品位的标识口径（ADR-062 §决策 3，闭合 §后果 E4）</b>：{@code hotSpuIds}
 * 为 SPU <b>业务键</b>（32-hex，{@code spu.spu_id}）—— 写读同型同义，与店铺装修的
 * 其余对外字段口径一致；此前存技术键（{@code spu.id}）的形态随本批收口。
 * 形态校验经 {@code IdGenerator.requireWire}（编解码唯一出口，非法即 400 fail-fast）。
 *
 * <p>Phase 1 取舍（ADR-048；ADR-062 §后果 E4 收口后仍成立）：{@code hotSpuIds}
 * <b>不校验商品存在性与归属</b>（跨域校验留 Phase 2）—— merchant 域不持有商品数据，
 * 而同库直读他域表做「业务判断」被 ADR-057 明令禁止（只允许展示读模型），
 * 走 Feign 又受「调 Feign 的 Manager 不得 @Transactional」硬约束约束且需新增端点
 * （ADR-062 §决策 4：新端点属显式决策）。展示端按需过滤失效 / 非在售商品。
 */
@Data
@Builder
public class ShopDecoration {

    /** 轮播图上限 */
    public static final int MAX_BANNERS = 5;

    /** 推荐商品位上限 */
    public static final int MAX_HOT_SPU_IDS = 8;

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

    /** 推荐商品位（SPU 业务键 32-hex，≤8，去重保序） */
    private List<String> hotSpuIds;

    // ==================== 工厂 ====================

    /**
     * 创建装修（创建即校验 + 归一化：公告去首尾空白、轮播逐条去空白并校验 URL、
     * 推荐位去重保序并按 32-hex 业务键形态校验）。
     *
     * @throws BizException shopId 缺失（PARAM_ERROR）、公告超长（PARAM_ERROR）、
     *                      轮播超 5 张或含非 http(s) 链接（PARAM_ERROR）、
     *                      推荐位形态非法或去重后超 8 个（PARAM_ERROR / 400）
     */
    public static ShopDecoration of(Long shopId, String announcement,
                                    List<String> banners, List<String> hotSpuIds) {
        if (shopId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "店铺 ID 不能为空");
        }
        return ShopDecoration.builder()
                .shopId(shopId)
                .announcement(normalizeAnnouncement(announcement))
                .banners(normalizeBanners(banners))
                .hotSpuIds(normalizeHotSpuIds(hotSpuIds))
                .build();
    }

    /** 未装修占位（S1c 空对象：公告为空、两个列表为空数组）。 */
    public static ShopDecoration blank() {
        return ShopDecoration.builder()
                .banners(List.of())
                .hotSpuIds(List.of())
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

    /**
     * 推荐位归一化：去空白 / 去 null / 去重保序，逐条做 <b>32-hex 业务键形态校验</b>。
     *
     * <p>形态校验走 {@code IdGenerator.requireWire}（ADR-062 §决策 7 的编解码唯一出口）：
     * 非法入参 → 400 {@code PARAM_ERROR} <b>fail-fast</b>，绝不静默丢弃或落库
     * （静默丢弃会让商家以为「保存成功」而实际少了推荐位）。
     */
    private static List<String> normalizeHotSpuIds(List<String> hotSpuIds) {
        if (hotSpuIds == null || hotSpuIds.isEmpty()) {
            return List.of();
        }
        List<String> distinct = hotSpuIds.stream()
                .filter(Objects::nonNull)
                .map(id -> IdGenerator.requireWire("hotSpuIds", id))
                .distinct()
                .toList();
        if (distinct.size() > MAX_HOT_SPU_IDS) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "推荐商品位最多 " + MAX_HOT_SPU_IDS + " 个");
        }
        return distinct;
    }
}
