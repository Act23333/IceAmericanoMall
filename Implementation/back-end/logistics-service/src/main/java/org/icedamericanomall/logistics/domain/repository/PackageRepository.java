package org.icedamericanomall.logistics.domain.repository;

import org.icedamericanomall.logistics.domain.model.logistics.Package;

import java.util.List;
import java.util.Optional;

/**
 * 物流包裹仓储（领域端口）—— 聚合的持久化契约。
 *
 * <p>实现方：{@code infrastructure.persistence.PackageRepositoryImpl}（MyBatis-Plus）。
 */
public interface PackageRepository {

    /** 查询订单全部包裹（按包裹号升序）。 */
    List<Package> findByOrderId(Long orderId);

    /** 定位指定包裹（未找到返回 empty，由调用方决定异常语义）。 */
    Optional<Package> findByOrderIdAndPackageNo(Long orderId, Integer packageNo);

    /** 同一 (订单, 包裹号) 是否已有记录（发货事件幂等依据）。 */
    boolean existsByOrderIdAndPackageNo(Long orderId, Integer packageNo);

    /** 新增包裹记录。 */
    void save(Package pkg);

    /** 更新包裹（含状态迁移结果）。 */
    void update(Package pkg);
}
