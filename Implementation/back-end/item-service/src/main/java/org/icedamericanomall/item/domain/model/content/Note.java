package org.icedamericanomall.item.domain.model.content;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 种草笔记聚合根（纯 POJO，零框架依赖）—— F12 内容社区 Phase 1（ADR-048）。
 *
 * <p><b>聚合边界</b>：{@code note} 行为本聚合根；点赞关联 {@code note_like}（笔记 × 用户）
 * 与关联商品 {@code note_spu}（笔记 × 商品，≤{@value #MAX_SPUS} 条）是<b>聚合内部
 * 关联</b>，分别只允许经 {@code NoteLikeRepository} / {@code NoteSpuRepository} 端口访问，
 * 外部不得直接操作这两张表。笔记与商品通过 {@code spuId} <b>按 ID 引用</b>，不持有
 * {@code Spu} 对象引用；发布时的商品存在性/在售校验属跨聚合协作，上移应用层编排。
 *
 * <p><b>包边界即未来拆分边界</b>（ADR-025/048）：内容生态膨胀（评论/话题/关注流）时
 * {@code domain/model/content/} 整包拆出 community-service，本次落位 item 域与 review 同构。
 *
 * <p><b>Phase 1 能力边界</b>（ADR-048）：纯文本正文 + 图集 ≤{@value #MAX_IMAGES}（不做富文本/视频）、
 * 关联商品 ≤{@value #MAX_SPUS}（发布即校验存在且在售）、点赞（uk 幂等 + 原子计数）、
 * 作者软删；不做笔记评论、关注流、推荐分发、草稿箱——status 恒 {@value #STATUS_PUBLISHED}
 * （发布即上架，无草稿态）。
 */
@Data
@Builder
public class Note {

    /** 状态：已发布（对外可见）。 */
    public static final int STATUS_PUBLISHED = 1;

    /** 状态：已删除（作者软删，流与详情不可见；物理删除需评审，留待数据治理）。 */
    public static final int STATUS_DELETED = 2;

    /** 图集上限（得物式九宫格）。 */
    public static final int MAX_IMAGES = 9;

    /** 关联商品上限（发布时逐个校验存在且在售）。 */
    public static final int MAX_SPUS = 5;

    /** 标题最大长度。 */
    public static final int TITLE_MAX_LEN = 50;

    /** 正文最大长度（Phase 1 纯文本）。 */
    public static final int CONTENT_MAX_LEN = 2000;

    /** 图片/封面 URL 合法口径：http(s) 绝对地址且无空白字符（领域内纯校验，不依赖网络探测）。 */
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://\\S+$");

    private Long id;

    /** 作者 user_id。 */
    private Long userId;

    private String title;

    private String content;

    /** 封面图（可空；流卡片优先展示）。 */
    private String coverImage;

    /** 图集（≤{@value #MAX_IMAGES} 个 URL；持久化为 JSON 数组字符串，转换在仓储实现）。 */
    private List<String> images;

    /** 状态（{@value #STATUS_PUBLISHED}=已发布 / {@value #STATUS_DELETED}=已删除）。 */
    private Integer status;

    /** 点赞数（冗余计数，仓储原子维护，聚合内存值仅查询视角）。 */
    private Integer likeCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /**
     * 发布种草笔记（未落库）—— 创建即校验不变式，status 恒为已发布（发布即上架，无草稿）。
     *
     * <p>校验失败抛 {@code PARAM_ERROR}（HTTP 400，P28 契约）。
     *
     * @param userId     作者 user_id
     * @param title      标题（必填 ≤{@value #TITLE_MAX_LEN}）
     * @param content    正文（必填 ≤{@value #CONTENT_MAX_LEN}）
     * @param coverImage 封面图 URL（可空）
     * @param images     图集（可空，≤{@value #MAX_IMAGES} 个合法 URL）
     * @return 新笔记聚合（未落库，id 由仓储回填）
     * @throws BizException 标题/正文缺失超长、图集超限或 URL 非法、封面 URL 非法
     */
    public static Note publish(Long userId, String title, String content,
                               String coverImage, List<String> images) {
        assertTextRule(title, content);
        List<String> safeImages = assertImagesRule(images);
        if (isNotBlank(coverImage) && !URL_PATTERN.matcher(coverImage).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "封面图 URL 非法");
        }
        return Note.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .coverImage(coverImage)
                .images(safeImages)
                .status(STATUS_PUBLISHED)
                .likeCount(0)
                .build();
    }

    // ==================== 查询 ====================

    /** 是否已发布（对外可见）。 */
    public boolean isPublished() {
        return status != null && status == STATUS_PUBLISHED;
    }

    /** 是否已软删。 */
    public boolean isDeleted() {
        return status != null && status == STATUS_DELETED;
    }

    /** 是否为该作者所写。 */
    public boolean isOwnedBy(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    // ==================== 业务断言 / 变更 ====================

    /**
     * 断言当前用户可删除本笔记（仅作者本人，P33 契约：非本人 → 403）。
     *
     * @throws BizException 非作者本人（{@code FORBIDDEN}）
     */
    public void assertDeletableBy(Long userId) {
        if (!isOwnedBy(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅作者本人可删除笔记");
        }
    }

    /** 作者软删（status → 已删除；关联 note_spu / note_like 保留，软删后不可达不清理）。 */
    public void softDelete() {
        this.status = STATUS_DELETED;
    }

    // ==================== 工厂内校验 ====================

    private static void assertTextRule(String title, String content) {
        if (!isNotBlank(title)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "标题不能为空");
        }
        if (title.length() > TITLE_MAX_LEN) {
            throw new BizException(ErrorCode.PARAM_ERROR, "标题长度不能超过" + TITLE_MAX_LEN);
        }
        if (!isNotBlank(content)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "正文不能为空");
        }
        if (content.length() > CONTENT_MAX_LEN) {
            throw new BizException(ErrorCode.PARAM_ERROR, "正文长度不能超过" + CONTENT_MAX_LEN);
        }
    }

    /**
     * 图集规则：可空、≤{@value #MAX_IMAGES} 个、每个 URL 合法；返回归一列表（null → 空列表）。
     */
    private static List<String> assertImagesRule(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        if (images.size() > MAX_IMAGES) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片数量不能超过" + MAX_IMAGES);
        }
        for (String image : images) {
            if (!isNotBlank(image) || !URL_PATTERN.matcher(image).matches()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "图片 URL 非法: " + image);
            }
        }
        return List.copyOf(images);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
