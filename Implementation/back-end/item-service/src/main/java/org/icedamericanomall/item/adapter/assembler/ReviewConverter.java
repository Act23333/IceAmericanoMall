package org.icedamericanomall.item.adapter.assembler;

import org.icedamericanomall.item.adapter.response.ReviewVO;
import org.icedamericanomall.item.domain.model.review.Review;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 评价转换器。
 *
 * <p><b>构建期守卫</b>：本仓 MapStruct 未映射目标属性为构建失败
 * （parent pom {@code -Amapstruct.unmappedTargetPolicy=ERROR}）—— 下列 {@code ignore} 均为
 * 「有意不映射」的显式声明。
 */
@Mapper(componentModel = "spring")
public interface ReviewConverter {

    /**
     * 聚合 → 评价 VO。
     *
     * <ul>
     *   <li>{@code likedByMe} —— 请求者相关字段，由 {@code ReviewManager.enrichVO} 按当前登录用户
     *       查询（fail-open：查询失败仅记日志，评价列表照常返回）；转换器不持有请求上下文。</li>
     *   <li>{@code username} / {@code avatar} —— <b>item 域无数据源</b>（评价表只存 userId，
     *       昵称/头像属用户域），当前恒 {@code null}：这是<b>已登记的契约缺口</b>（contract/05 的
     *       ReviewVO 样例含这两字段），按 ADR-057 应由「应用层读模型端口 + fail-open」补齐，
     *       未在本批脚手架内实现 —— 显式 ignore 使其保持可见，不得靠默默警告掩盖。</li>
     * </ul>
     */
    @Mapping(target = "likedByMe", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    ReviewVO toVO(Review review);
}
