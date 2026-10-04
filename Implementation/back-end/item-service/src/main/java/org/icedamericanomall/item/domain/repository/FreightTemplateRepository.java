package org.icedamericanomall.item.domain.repository;

import org.icedamericanomall.item.domain.model.freight.FreightTemplate;

import java.util.List;

/**
 * 运费模板仓储（领域端口）—— 实现方：{@code infrastructure.persistence.FreightTemplateRepositoryImpl}。
 */
public interface FreightTemplateRepository {

    /** 按 ID 载入（可为 null；「不存在或非本商家」由应用层统一按 3041 处理）。 */
    FreightTemplate findById(Long id);

    /** 同商家内模板名是否重复（排除自身 id，用于改名）。 */
    boolean existsDuplicateName(Long sellerId, String templateName, Long excludeId);

    /** 新增（返回回填技术主键后的聚合）。 */
    FreightTemplate save(FreightTemplate template);

    /** 更新。 */
    void update(FreightTemplate template);

    /** 删除（绑定商品由 FK ON DELETE SET NULL 回落默认模板）。 */
    void deleteById(Long id);

    /** 商家全部模板（id 升序）。 */
    List<FreightTemplate> findBySeller(Long sellerId);

    /** 启用模板（该商家 + 指定 ids，id 升序；I43 计价查询）。 */
    List<FreightTemplate> findEnabledByIds(Long sellerId, List<Long> templateIds);

    /** 启用模板（该商家全部，id 升序）。 */
    List<FreightTemplate> findEnabledBySeller(Long sellerId);

    /** 商家默认模板 = 最早创建的启用模板（无则 null）。 */
    FreightTemplate findDefaultEnabled(Long sellerId);
}
