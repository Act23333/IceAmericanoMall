package org.icedamericanomall.user.domain.repository;

import org.icedamericanomall.user.domain.model.Address;

import java.util.List;
import java.util.Optional;

/**
 * 收货地址聚合仓储（领域层定义，基础设施层实现）。
 *
 * <p>地址的业务键 = {@code userId + id}：查询 / 更新 / 删除均在 WHERE 中携带 userId，
 * 与实体方法 {@code Address#assertOwnedBy} 形成"业务校验 + SQL 兜底"双层越权防范。
 * 不存在按裸 id 的 update / delete 方法。
 */
public interface AddressRepository {

    /** 查询用户全部地址（默认地址在前）。 */
    List<Address> findByUserId(Long userId);

    /** 按 id 查询（内部 Feign 快照用；归属校验由调用方按需进行）。 */
    Optional<Address> findById(Long id);

    /** 新增地址（回填技术主键）。 */
    Address insert(Address address);

    /** 按 {userId, id} 更新地址。 */
    int updateByUserIdAndId(Address address);

    /** 按 {userId, id} 删除地址。 */
    int deleteByUserIdAndId(Long userId, Long id);

    /** 清除用户所有默认标记（设置新默认前调用）。 */
    int clearDefault(Long userId);

    /** 按 {userId, id} 标记默认地址。 */
    int markDefault(Long userId, Long id);
}
