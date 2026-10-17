package org.icedamericanomall.item.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.NotificationClient;
import org.icedamericanomall.client.TaskClient;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.InAppMessageSendReqCO;
import org.icedamericanomall.dto.TaskCompleteCO;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.item.adapter.assembler.ReviewConverter;
import org.icedamericanomall.item.adapter.request.ReviewCreateReq;
import org.icedamericanomall.item.adapter.response.ReviewSummaryVO;
import org.icedamericanomall.item.adapter.response.ReviewVO;
import org.icedamericanomall.item.app.dto.SkuSpuDisplay;
import org.icedamericanomall.item.app.port.SkuSpuDisplayQuery;
import org.icedamericanomall.item.domain.model.spu.Spu;
import org.icedamericanomall.item.domain.model.review.Review;
import org.icedamericanomall.item.domain.repository.SpuRepository;
import org.icedamericanomall.item.domain.repository.ReviewRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 评价编排（应用层）—— 评价用例（发布/分页/汇总/回复/追评/点赞）+ 站内信通知触发
 * + 每日评价任务旁路触发（F9，ADR-047）。
 *
 * <p>跨服务调用（notification-service / marketing-service）只允许出现在本层，且一律经
 * {@link #runAfterCommit} 在<b>事务提交后</b>发起（硬约束：Feign 严禁进事务）；
 * 回复/追评/点赞的守卫 SQL（reply IS NULL、本人追评）在仓储实现内。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewManager {

    private static final String NOTIFY_TYPE_REPLY = "REPLY";
    private static final String NOTIFY_TYPE_LIKE = "LIKE";
    private static final String NOTIFY_TITLE_REPLY = "商家回复了你的评价";
    private static final String NOTIFY_TITLE_LIKE = "有人赞了你的评价";
    private static final String PRODUCT_LINK_PREFIX = "/product/";
    /** F9 每日任务编码（ADR-047 task_def.code seed 常量，对应「每日发表 1 条评价」）。 */
    private static final String TASK_CODE_DAILY_REVIEW = "DAILY_REVIEW";

    private final ReviewRepository reviewRepository;
    private final SpuRepository spuRepository;
    private final SkuSpuDisplayQuery skuSpuDisplayQuery;
    private final ReviewConverter reviewConverter;
    private final NotificationClient notificationClient;
    private final TaskClient taskClient;

    /**
     * 发表评价（同订单同用户去重），累加商品评论数，并在事务提交后触发每日评价任务（F9）。
     *
     * <p><b>标识口径（ADR-061 §决策 2）</b>：入参 {@code spuId}/{@code skuId} 均为
     * <b>SPU/SKU 业务键</b>（32-hex）；本层用同库只读索引（{@code spu.spu_id} /
     * {@code sku.sku_id}）解析为本域技术键后再落库 —— <b>表结构不动</b>（A 案）。
     * 解析不到的键属参数缺陷 → {@code PARAM_ERROR} fail-fast（写路径不静默留空）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO createReview(Long userId, ReviewCreateReq req) {
        String orderNo = normalizeOrderNo(req.getOrderNo());
        if (reviewRepository.countByOrderNoAndUserId(orderNo, userId) > 0) {
            throw new BizException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        Long spuId = resolveSpuTechnicalId(req.getSpuId());
        Long skuId = resolveSkuTechnicalId(req.getSkuId());
        Review review = Review.post(userId, spuId, orderNo, skuId,
                req.getRating(), req.getContent(), req.getImages());
        reviewRepository.save(review);
        spuRepository.incrementCommentCount(spuId);
        triggerDailyReviewTask(userId);
        ReviewVO vo = reviewConverter.toVO(review);
        fillBusinessKeys(List.of(vo));
        return vo;
    }

    /**
     * 订单号归一（ADR-050 三批：跨域引用收敛为业务键）：必须为 32 位 hex/36 位连字符 UUID
     * （来源 {@code OrderVO.orderNo}，现有契约缺口一并修复——此前 orderId(Long) 前端无从获得），
     * 非法输入 fail-fast {@code PARAM_ERROR}。
     */
    private static String normalizeOrderNo(String orderNo) {
        try {
            return IdGenerator.toHexString(IdGenerator.toBytes(orderNo));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "orderNo 必须为 32 位 hex 或 36 位连字符 UUID: " + orderNo);
        }
    }

    /**
     * 商品评价分页（创建时间倒序，带 likedByMe 回填 + 业务键回填）。
     *
     * @param spuId SPU <b>业务键</b>（32-hex，路径变量 {@code {spuId}} 口径，ADR-062 §决策 2）；
     *              商品不存在/已删除 → 空页（fail-open，列表位不放大成异常）
     */
    public IPage<ReviewVO> pageBySpu(String spuId, int page, int size) {
        Long technicalId = spuTechnicalIdOrNull(spuId);
        if (technicalId == null) {
            return new Page<>(page, size);
        }
        IPage<ReviewVO> result = reviewRepository.pageBySpu(technicalId, page, size)
                .convert(r -> enrichVO(reviewConverter.toVO(r)));
        fillBusinessKeys(result.getRecords());
        return result;
    }

    /** 商品评价分页（星级/有图过滤 + 排序归一）。入参口径同 {@link #pageBySpu}。 */
    public IPage<ReviewVO> pageBySpuFiltered(String spuId, Integer rating, Boolean hasMedia,
                                                 String sort, int page, int size) {
        Long technicalId = spuTechnicalIdOrNull(spuId);
        if (technicalId == null) {
            return new Page<>(page, size);
        }
        IPage<ReviewVO> result = reviewRepository.pageFiltered(technicalId, rating, hasMedia, sort, page, size)
                .convert(r -> enrichVO(reviewConverter.toVO(r)));
        fillBusinessKeys(result.getRecords());
        return result;
    }

    /**
     * 评价汇总 —— 一次 GROUP BY 聚合得到星级分布；rating 为 null 或超出 1~5 的
     * 历史脏数据被忽略，不参与均分。入参口径同 {@link #pageBySpu}（业务键；不存在 → 零值汇总）。
     */
    public ReviewSummaryVO reviewSummary(String spuId) {
        Long technicalId = spuTechnicalIdOrNull(spuId);
        if (technicalId == null) {
            return emptySummary();
        }
        long total = 0L;
        long weightedSum = 0L;
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = Review.MIN_RATING; star <= Review.MAX_RATING; star++) {
            distribution.put(star, 0L);
        }
        List<org.icedamericanomall.item.domain.model.review.RatingCount> rows =
                reviewRepository.countByRating(technicalId);
        if (rows != null) {
            for (org.icedamericanomall.item.domain.model.review.RatingCount row : rows) {
                Integer rating = row.rating();
                if (rating == null || rating < Review.MIN_RATING || rating > Review.MAX_RATING) {
                    continue;
                }
                long count = row.countOrZero();
                distribution.merge(rating, count, Long::sum);
                total += count;
                weightedSum += (long) rating * count;
            }
        }
        ReviewSummaryVO summary = new ReviewSummaryVO();
        summary.setTotalCount(total);
        summary.setDistribution(distribution);
        summary.setAverageScore(total == 0L ? 0.0 : Math.round(weightedSum * 10.0 / total) / 10.0);
        return summary;
    }

    /** 零值汇总（商品不存在时的空对象，分布按 1~5 星全零）。 */
    private static ReviewSummaryVO emptySummary() {
        ReviewSummaryVO summary = new ReviewSummaryVO();
        summary.setTotalCount(0L);
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = Review.MIN_RATING; star <= Review.MAX_RATING; star++) {
            distribution.put(star, 0L);
        }
        summary.setDistribution(distribution);
        summary.setAverageScore(0.0);
        return summary;
    }

    /**
     * SPU <b>业务键</b>（32-hex）→ 本域技术键（{@code spu.id}）。
     *
     * <p>解析只许在服务内完成（ADR-062 §决策 3）：同库 {@code uk_spu_id} 唯一索引一次查询。
     * 读路径（评价列表/汇总）取不到商品时按「无评价」处理返回 {@code null}（fail-open，
     * 与既有「未知 id → 空列表」语义一致）——写路径的 fail-closed 口径见 {@link #resolveSpuTechnicalId}.
     */
    private Long spuTechnicalIdOrNull(String spuBusinessId) {
        if (spuBusinessId == null || spuBusinessId.isBlank()) {
            return null;
        }
        Spu spu = spuRepository.findByBusinessId(spuBusinessId);
        return spu == null ? null : spu.getId();
    }

    /**
     * 点赞/取消点赞（仓储幂等矩阵），点赞成功时通知评价作者。
     *
     * <p><b>事务纪律（硬约束：Feign 严禁进事务）</b>：本方法带写事务，故站内信通知经
     * {@link #runAfterCommit} 移到<b>事务提交后</b>发送（回滚即不发送）；通知本身 fail-open。
     *
     * @return 1=点赞成功，-1=取消点赞
     */
    @Transactional(rollbackFor = Exception.class)
    public int toggleLike(Long userId, Long reviewId) {
        int exists = reviewRepository.countLikeByUser(reviewId, userId);
        int result;
        if (exists > 0) {
            reviewRepository.deleteLike(reviewId, userId);
            reviewRepository.decrementLike(reviewId);
            result = -1;
        } else {
            reviewRepository.insertLike(reviewId, userId);
            reviewRepository.incrementLike(reviewId);
            result = 1;
        }
        if (result > 0) {
            Review review = reviewRepository.findById(reviewId);
            if (review != null) {
                Long recipientId = review.getUserId();
                Long spuId = review.getSpuId();
                runAfterCommit(() -> notifyReviewAuthor(recipientId, userId, NOTIFY_TYPE_LIKE,
                        NOTIFY_TITLE_LIKE, null, spuId));
            }
        }
        return result;
    }

    /**
     * 商家回复评价（商品归属校验），成功后通知评价作者。
     *
     * <p><b>事务纪律（硬约束：Feign 严禁进事务）</b>：通知经 {@link #runAfterCommit}
     * 移到事务提交后发送（回滚即不发送）；通知本身 fail-open，不阻断回复主流程。
     *
     * <p><b>错误语义（批次 ①「not-found 归位」）</b>：取不到评价 =
     * <b>资源不存在</b> → {@link ErrorCode#NO_RESOURCE_FOUND}（1103 / HTTP 404）。
     * 改造前借用 {@code BUSINESS_EXECUTION_EXCEPTION}（3000 / 200），与紧随其后的
     * 「已回复过该评价」（真·业务规则拒绝，仍是 3000 / 200）挤在同一状态码里，
     * 调用方无法区分「评价没了」与「评价已回复」。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replyToReview(Long sellerId, Long reviewId, String content) {
        Review review = reviewRepository.findById(reviewId);
        if (review == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "评价不存在");
        }
        Spu spu = spuRepository.findById(review.getSpuId());
        if (spu == null || !spu.getSellerId().equals(sellerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能回复自己店铺商品的评价");
        }
        if (reviewRepository.addReply(reviewId, content) == 0) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已回复过该评价");
        }
        log.info("商家回复评价: reviewId={}, sellerId={}", reviewId, sellerId);
        Long recipientId = review.getUserId();
        Long spuId = review.getSpuId();
        runAfterCommit(() -> notifyReviewAuthor(recipientId, sellerId, NOTIFY_TYPE_REPLY,
                NOTIFY_TITLE_REPLY, content, spuId));
    }

    /** 用户追评（非本人/已追评由仓储守卫拒绝）。 */
    @Transactional(rollbackFor = Exception.class)
    public void appendReview(Long userId, Long reviewId, String content, String mediaUrls) {
        if (reviewRepository.addAppend(reviewId, userId, content, mediaUrls) == 0) {
            throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "追评失败：评价不存在、非本人评价或已追评");
        }
        log.info("用户追评: reviewId={}, userId={}", reviewId, userId);
    }

    /** 评价作者回填 likedByMe（失败不影响评价主流程，仅记日志）。 */
    private ReviewVO enrichVO(ReviewVO vo) {
        try {
            Long userId = UserContext.getUserId();
            if (userId != null) {
                vo.setLikedByMe(reviewRepository.countLikeByUser(vo.getId(), userId) > 0);
            }
        } catch (Exception e) {
            log.warn("填充点赞状态失败: reviewId={}, cause={}", vo.getId(), e.getMessage());
        }
        return vo;
    }

    // ==================== 标识口径（ADR-058 §3 / ADR-061 §决策 2） ====================

    /**
     * SPU 业务键（32-hex，线格式入参）→ 本域技术键。
     *
     * <p>同库只读解析（{@code spu.spu_id} 唯一索引，A 案：表结构不动）。缺失/非法/不存在
     * 一律 {@code PARAM_ERROR}——<b>写路径 fail-fast</b>，不静默落一条指向不存在商品的评价。
     */
    private Long resolveSpuTechnicalId(String spuBusinessId) {
        Map<String, Long> resolved = spuRepository.idMapByBusinessIds(
                spuBusinessId == null ? List.of() : List.of(spuBusinessId));
        Long spuId = resolved.get(spuBusinessId);
        if (spuId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "商品不存在或 spuId 非法（应为 32 位 hex 业务键）: " + spuBusinessId);
        }
        return spuId;
    }

    /** SKU 业务键（32-hex，可空）→ 本域技术键；未提供返回 {@code null}，提供但解析不到 fail-fast。 */
    private Long resolveSkuTechnicalId(String skuBusinessId) {
        if (skuBusinessId == null || skuBusinessId.isBlank()) {
            return null;
        }
        Map<String, SkuSpuDisplay> found = skuSpuDisplayQuery.findBySkuBusinessIds(List.of(skuBusinessId));
        SkuSpuDisplay display = found == null ? null : found.get(skuBusinessId);
        if (display == null || display.skuId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "SKU 不存在或 skuId 非法（应为 32 位 hex 业务键）: " + skuBusinessId);
        }
        return display.skuId();
    }

    /**
     * 批量回填线格式业务键（{@code ReviewVO.spuId} / {@code skuId}）。
     *
     * <p><b>为何必须回填</b>：{@code review} 表只存技术外键（{@code spu.id} / {@code sku.id}，
     * 与订单/收藏/足迹一致，ADR-061 §决策 4），而线格式标识一律业务键 32-hex（§决策 2）。
     *
     * <p><b>批量</b>：整页的 SPU 技术键一次 {@code SpuRepository#businessIdMapByTechnicalIds}
     * 反向解析（{@code spu} 主键 IN）、整页 SKU 技术键一次 {@code SkuSpuDisplayQuery#findBySkuIds}
     * 批量取数 —— <b>两条 SQL 服务整页</b>，禁止逐行调用（无 N+1）。
     *
     * <p><b>fail-open</b>：解析异常/未命中 → 相应字段留 {@code null} + WARN，评价行照常返回
     * （与 {@link #enrichVO} 同口径，展示字段失败不得让评价列表少一条或整页失败）。
     */
    private void fillBusinessKeys(List<ReviewVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        List<Long> spuIds = vos.stream().map(ReviewVO::getSpuId)
                .filter(Objects::nonNull).map(ReviewManager::parseTechnicalId)
                .filter(Objects::nonNull).distinct().toList();
        List<Long> skuIds = vos.stream().map(ReviewVO::getSkuId)
                .filter(Objects::nonNull).map(ReviewManager::parseTechnicalId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> spuBusinessKeys = spuBusinessKeysById(spuIds);
        Map<Long, String> skuBusinessKeys = skuBusinessKeysById(skuIds);
        for (ReviewVO vo : vos) {
            Long spuId = parseTechnicalId(vo.getSpuId());
            if (spuId != null) {
                String key = spuBusinessKeys.get(spuId);
                if (key == null) {
                    log.warn("评价商品业务键回填未命中（fail-open，字段留空）: spuId={}", spuId);
                }
                vo.setSpuId(key);
            }
            Long skuId = parseTechnicalId(vo.getSkuId());
            if (skuId != null) {
                vo.setSkuId(skuBusinessKeys.get(skuId));
            }
        }
    }

    /** SPU 技术键集合 → 业务键映射（异常 fail-open 返回空 Map）。 */
    private Map<Long, String> spuBusinessKeysById(List<Long> spuIds) {
        if (spuIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, String> resolved = spuRepository.businessIdMapByTechnicalIds(spuIds);
            return resolved == null ? Map.of() : resolved;
        } catch (Exception e) {
            log.warn("评价商品业务键批量解析失败，字段留空（fail-open）: cause={}", e.getMessage());
            return Map.of();
        }
    }

    /** SKU 技术键集合 → 业务键映射（复用 I13 只读联表读模型；异常 fail-open 返回空 Map）。 */
    private Map<Long, String> skuBusinessKeysById(List<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<Long, SkuSpuDisplay> displays = skuSpuDisplayQuery.findBySkuIds(skuIds);
            if (displays == null || displays.isEmpty()) {
                return Map.of();
            }
            Map<Long, String> result = new LinkedHashMap<>(displays.size());
            displays.forEach((technicalId, display) -> {
                if (technicalId != null && display != null && display.skuBusinessId() != null) {
                    result.putIfAbsent(technicalId, display.skuBusinessId());
                }
            });
            return result;
        } catch (Exception e) {
            log.warn("评价 SKU 业务键批量解析失败，字段留空（fail-open）: cause={}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * VO 上的技术键数字串 → {@code Long}（转换器把 {@code Review.spuId} 映为数值型字符串）。
     * 非数值形态返回 {@code null}（fail-open：不在装配阶段抛错，交由上方留空处理）。
     */
    private static Long parseTechnicalId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 发送站内信（经 notification-service 落库）；失败只记日志，不影响评价主流程。
     */
    private void notifyReviewAuthor(Long recipientId, Long senderId, String type,
                                    String title, String content, Long spuId) {
        try {
            InAppMessageSendReqCO message = new InAppMessageSendReqCO();
            message.setRecipientId(recipientId);
            message.setSenderId(senderId);
            // 不查用户名：senderName 由前端按 senderId 渲染（昵称兜底已随边界重构移除）
            message.setSenderName("");
            message.setSenderAvatar("");
            message.setType(type);
            message.setTitle(title);
            message.setContent(content != null ? content : "");
            message.setLinkUrl(spuId != null ? PRODUCT_LINK_PREFIX + spuId : "");
            notificationClient.sendInAppMessage(message);
        } catch (Exception e) {
            log.warn("发送评价通知失败(type={}, recipient={}): {}", type, recipientId, e.getMessage());
        }
    }

    /**
     * 每日评价任务旁路触发（F9 DAILY_REVIEW，ADR-047）—— 评价落库成功后触发。
     *
     * <p><b>事务纪律（硬约束：Feign 严禁进事务）</b>：调用点处于 {@code createReview} 的
     * 写事务内，故对齐 {@link SpuRecommendManager} 摄取先例 —— 走 {@link #runAfterCommit}，
     * 有活动事务时注册 {@code afterCommit} 回调（回滚即不触发，提交后才远程调用）。
     */
    private void triggerDailyReviewTask(Long userId) {
        runAfterCommit(() -> completeDailyReviewTask(userId));
    }

    /**
     * 事务提交后执行（<b>Feign 严禁进事务</b>，硬约束）—— 本类所有跨服务调用的统一出口。
     *
     * <p>有活动事务（{@code TransactionSynchronizationManager} 已同步）时注册 {@code afterCommit}
     * 回调：主事务回滚则回调不执行，提交后才发起远程调用，从而不会把 Feign 的耗时与失败
     * 混进本地写事务（连接池占用 / 回滚语义错乱）。无活动事务（单测直调、非事务入口）时直接执行。
     *
     * <p>回调内的失败语义由 {@code action} 自负（本类传入的两处均 fail-open）。
     */
    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /** 任务触发（fail-open）：降级返回 {@code completed=false}（返回值忽略）或异常均只记日志，评价主流程零感知。 */
    private void completeDailyReviewTask(Long userId) {
        try {
            taskClient.complete(new TaskCompleteCO(userId, TASK_CODE_DAILY_REVIEW));
        } catch (Exception e) {
            log.warn("每日任务触发失败（不阻断评价主流程）: userId={}", userId, e);
        }
    }
}
