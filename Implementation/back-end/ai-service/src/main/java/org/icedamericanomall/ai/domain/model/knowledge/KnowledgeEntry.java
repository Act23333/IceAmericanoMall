package org.icedamericanomall.ai.domain.model.knowledge;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家知识库条目（知识库聚合实体，纯 POJO，零框架依赖）—— {@code merchant_knowledge_base} 表的领域投影。
 *
 * <p>状态语义（契约值，详见数据模型文档）：2=生效，3=已删除（软删）。
 * {@code embeddingId} 是向量索引归属的内部句柄（成功同步后为 {@code es:{knowledgeId}}），
 * 仅在领域内流转，禁止下传到 DTO/VO。
 */
@Data
public class KnowledgeEntry {

    /** 新建条目状态：生效。 */
    public static final int STATUS_ACTIVE = 2;

    /** 软删除状态。 */
    public static final int STATUS_DELETED = 3;

    /** 技术主键（落库后由仓储回填；领域内不作为操作键）。 */
    private Long id;

    /** 归属商家ID（数据隔离键，所有读写都必须限定）。 */
    private Long sellerId;

    /** 条目标题。 */
    private String title;

    /** 条目正文（向量摄取的原文来源）。 */
    private String content;

    /** 分类（可空，列表筛选维度）。 */
    private String category;

    /** 向量索引归属标识（未同步为 null；不下传出参）。 */
    private String embeddingId;

    /** 状态：{@link #STATUS_ACTIVE} / {@link #STATUS_DELETED}。 */
    private Integer status;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;

    /**
     * 应用局部更新补丁：仅覆盖非空入参（与原应用层合并语义一致）。
     *
     * @param title    新标题（null 表示不更新）
     * @param content  新正文（null 表示不更新）
     * @param category 新分类（null 表示不更新）
     */
    public void mergePatch(String title, String content, String category) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (category != null) {
            this.category = category;
        }
    }

    /**
     * 是否处于软删除状态。
     *
     * @return true 表示已删除
     */
    public boolean isDeleted() {
        return status != null && status == STATUS_DELETED;
    }
}
