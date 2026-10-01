package org.icedamericanomall.user.domain.repository;

import org.icedamericanomall.user.domain.model.Seller;
import org.icedamericanomall.user.domain.model.SellerStatusEnum;

import java.util.List;
import java.util.Optional;

/**
 * 商家聚合仓储（领域层定义，基础设施层实现）。
 *
 * <p>业务唯一键：{@code userId}（一个用户至多一个商家，跨聚合不变量由
 * {@code SellerRegistrar} 领域服务 + uk 约束共同保证）。
 */
public interface SellerRepository {

    /** 按用户 ID 查询商家（业务唯一键）。 */
    Optional<Seller> findByUserId(Long userId);

    /** 按用户 ID 查询"有效"商家（status=NORMAL 且用于公开店铺页）。 */
    Optional<Seller> findActiveByUserId(Long userId);

    /** 按技术主键查询（管理后台审核）。 */
    Optional<Seller> findById(Long id);

    /** 新增商家（入驻申请）。 */
    Seller insert(Seller seller);

    /** 管理后台：变更状态（保留既有宽松语义，不做状态机校验）。 */
    int updateStatusById(Long id, SellerStatusEnum status);

    /** 更新店铺信息（部分字段，null 忽略）。 */
    int updateShopInfo(Seller seller);

    /** 按状态分页查询（待审核列表）。 */
    List<Seller> findPageByStatus(SellerStatusEnum status, int offset, int size);

    /** 按状态计数（分页 total）。 */
    long countByStatus(SellerStatusEnum status);
}
