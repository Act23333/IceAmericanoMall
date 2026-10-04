package org.icedamericanomall.merchant.domain.repository;

import org.icedamericanomall.merchant.domain.enums.MerchantStatusEnum;
import org.icedamericanomall.merchant.domain.model.merchant.Merchant;

import java.util.List;
import java.util.Optional;

/**
 * 商家聚合仓储（领域层定义，基础设施层实现）。
 *
 * <p>业务唯一键：{@code userId}（一个用户至多一个商家，跨聚合不变量由
 * {@code MerchantRegistrar} 领域服务 + {@code uk_user_id} 约束共同保证：
 * 并发重复申请由持久化层把 {@code DuplicateKeyException} 翻译为业务异常）。
 *
 * <p>状态列不提供"直写"入口：外部先经 {@link Merchant} 的合法迁移方法，
 * 再由 {@link #updateStatus(Merchant)} 落库（ADR-019 收紧宽松语义）。
 */
public interface MerchantRepository {

    /** 按用户 ID 查询商家（业务唯一键）。 */
    Optional<Merchant> findByUserId(Long userId);

    /** 按技术主键查询（管理后台审核）。 */
    Optional<Merchant> findById(Long id);

    /**
     * 新增商家（入驻申请）。
     *
     * @throws org.icedamericanomall.common.exception.BizException 该用户已有商家
     *         （并发场景由唯一键冲突翻译，USER_ALREADY_EXISTS）
     */
    Merchant insert(Merchant merchant);

    /** 落库聚合状态迁移结果（合法性由 {@link Merchant} 实体方法保证）。 */
    int updateStatus(Merchant merchant);

    /** 按状态分页查询（待审核列表）。 */
    List<Merchant> findPageByStatus(MerchantStatusEnum status, int offset, int size);

    /** 按状态计数（分页 total）。 */
    long countByStatus(MerchantStatusEnum status);
}
