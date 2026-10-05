package org.icedamericanomall.item.domain.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.icedamericanomall.item.domain.model.kg.PairingRule;

import java.util.List;

/**
 * 类目搭配规则仓储（领域端口）—— 实现方：{@code infrastructure.persistence.kg.PairingRuleRepositoryImpl}。
 *
 * <p>真相源 {@code pairing_rule}（F15 Phase 1 唯一新增表）；治理写面（AD61-63）与
 * 重建读面（AD64）经本端口访问，禁止泄漏 PO。
 */
public interface PairingRuleRepository {

    /**
     * 治理分页（AD61，src/dst/status 三维可选过滤，id 升序稳定排序）。
     *
     * @param srcCategoryId 源类目过滤（null = 不过滤）
     * @param dstCategoryId 目标类目过滤（null = 不过滤）
     * @param status        状态过滤（null = 不过滤）
     * @param pageNum       页码（1 起）
     * @param pageSize      页大小
     * @return 规则分页
     */
    IPage<PairingRule> page(Long srcCategoryId, Long dstCategoryId, Integer status, int pageNum, int pageSize);

    /** 按技术主键载入（AD63 寻址口径；可为 null）。 */
    PairingRule findById(Long id);

    /** 按业务唯一键（uk_src_dst）载入（创建防重先查；可为 null）。 */
    PairingRule findBySrcAndDst(Long srcCategoryId, Long dstCategoryId);

    /** 新增（返回回填技术主键后的聚合；唯一键冲突由调用方捕获 DuplicateKey 兜底）。 */
    PairingRule save(PairingRule rule);

    /** 覆盖更新（AD63 null-safe PATCH 后整行落库）。 */
    void update(PairingRule rule);

    /** 指定源类目的全部规则（含停用——投影需按 status 原样写入边文档）。 */
    List<PairingRule> findAllBySrc(Long srcCategoryId);

    /** 全量规则（AD64 重建读面；含停用）。 */
    List<PairingRule> findAll();
}
