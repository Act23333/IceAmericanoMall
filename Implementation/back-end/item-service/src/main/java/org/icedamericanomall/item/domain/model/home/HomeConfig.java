package org.icedamericanomall.item.domain.model.home;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.item.domain.enums.HomeConfigStatusEnum;

import java.time.LocalDateTime;

/**
 * 首页装修配置聚合根（纯 POJO，零框架依赖）。
 *
 * <p>管理后台维护、前台只读启用项；{@code productIds} 是<b>商品 ID 列表快照串</b>
 * （JSON/逗号分隔，历史口径），不经聚合间对象引用。
 */
@Data
@Builder
public class HomeConfig {

    private Long id;

    /** 位置标识（如 {@code banner} / {@code recommend}）。 */
    private String slot;

    private Integer type;

    private String title;

    /** 关联商品 ID 列表（快照串，历史口径）。 */
    private String productIds;

    private String image;

    private String linkUrl;

    private Integer sortOrder;

    /** 状态（{@link HomeConfigStatusEnum} code）。 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 工厂 ====================

    /**
     * 新建配置（未落库）—— 状态缺省回落「启用」（与历史实现一致）。
     *
     * @param status 原始状态码（null 或非法 → 启用）
     */
    public static HomeConfig newConfig(String slot, Integer type, String title, String productIds,
                                       String image, String linkUrl, Integer sortOrder, Integer status) {
        return HomeConfig.builder()
                .slot(slot)
                .type(type)
                .title(title)
                .productIds(productIds)
                .image(image)
                .linkUrl(linkUrl)
                .sortOrder(sortOrder)
                .status(normalizeStatus(status))
                .build();
    }

    /**
     * 构造"编辑态"配置（仅用于落库更新，不读库）。
     *
     * <p>口径与历史实现一致：编辑为<b>整字段覆盖</b>（{@code null} 字段因
     * {@code update-strategy=not_null} 不落库 = 保持原值），响应回显请求值。
     * 存在性校验由应用层在调用前完成（历史实现同样先查存在）。
     */
    public static HomeConfig forRevise(Long id, String slot, Integer type, String title, String productIds,
                                       String image, String linkUrl, Integer sortOrder, Integer status) {
        return HomeConfig.builder()
                .id(id)
                .slot(slot)
                .type(type)
                .title(title)
                .productIds(productIds)
                .image(image)
                .linkUrl(linkUrl)
                .sortOrder(sortOrder)
                .status(status)
                .build();
    }

    /** 状态归一：未知码回落启用。 */
    private static Integer normalizeStatus(Integer status) {
        HomeConfigStatusEnum statusEnum = HomeConfigStatusEnum.of(status);
        return statusEnum == null ? HomeConfigStatusEnum.ENABLED.getCode() : statusEnum.getCode();
    }
}
