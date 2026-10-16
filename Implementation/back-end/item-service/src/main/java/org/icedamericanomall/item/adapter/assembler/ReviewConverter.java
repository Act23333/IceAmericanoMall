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
     *   <li>{@code spuId} / {@code skuId} —— <b>两步走</b>：本转换器先把聚合持有的技术外键
     *       （{@code Long}）按数值型字符串落到 VO，再由 {@code ReviewManager#fillBusinessKeys}
     *       用同库只读批量查询<b>替换为 32-hex 业务键</b>（ADR-058 §3 / ADR-061 §决策 2：
     *       聚合只持技术外键，业务键由应用层拼装；fail-open，未命中留空）。</li>
     *   <li>{@code likedByMe} —— 请求者相关字段，由 {@code ReviewManager.enrichVO} 按当前登录用户
     *       查询（fail-open：查询失败仅记日志，评价列表照常返回）；转换器不持有请求上下文。</li>
     *   <li>{@code username} / {@code avatar} —— <b>item 域无数据源</b>（评价表只存 userId，
     *       昵称/头像属用户域），当前恒 {@code null}：这是<b>已登记的契约缺口</b>（contract/05 的
     *       ReviewVO 样例含这两字段），按 ADR-057 应由「应用层读模型端口 + fail-open」补齐，
     *       未在本批脚手架内实现 —— 显式 ignore 使其保持可见，不得靠默默警告掩盖。</li>
     * </ul>
     */
    @Mapping(target = "spuId", expression = "java(review.getSpuId() == null ? null : review.getSpuId().toString())")
    @Mapping(target = "skuId", expression = "java(review.getSkuId() == null ? null : review.getSkuId().toString())")
    @Mapping(target = "likedByMe", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    ReviewVO toVO(Review review);
}
