package org.icedamericanomall.user.app.address;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.DBException;
import org.icedamericanomall.user.adapter.request.AddressReq;
import org.icedamericanomall.user.adapter.response.AddressResp;
import org.icedamericanomall.user.domain.model.address.Address;
import org.icedamericanomall.user.domain.repository.AddressRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址管理（应用层编排）。
 *
 * <p>越权防范双层：实体 {@code assertOwnedBy} 抛业务错误（用户可读），
 * 仓储 WHERE 携带 userId 兜底（SQL 级）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AddressManager {

    private final AddressRepository addressRepository;

    /** 当前用户地址列表。 */
    public List<AddressResp> listByUser(Long userId) {
        return addressRepository.findByUserId(userId).stream()
                .map(AddressResp::from)
                .toList();
    }

    /** 新增地址（归属取自登录上下文，不信任前端传参）。 */
    @Transactional(rollbackFor = Exception.class)
    public void addAddress(Long userId, AddressReq req) {
        Address address = toDomain(req);
        address.setUserId(userId);
        addressRepository.insert(address);
    }

    /** 查询地址详情。 */
    public AddressResp getById(Long id) {
        return addressRepository.findById(id)
                .map(AddressResp::from)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND, "地址不存在"));
    }

    /** 修改地址（先验归属，再按 {userId, id} 更新）。 */
    @Transactional(rollbackFor = Exception.class)
    public void updateAddress(Long userId, Long id, AddressReq req) {
        Address existing = loadOwned(userId, id);
        merge(existing, req);
        if (addressRepository.updateByUserIdAndId(existing) <= 0) {
            throw new DBException(ErrorCode.INTERNAL_ERROR, "地址修改失败！");
        }
    }

    /** 设为默认地址（先清旧默认，再标记新默认，同一事务）。 */
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long id) {
        loadOwned(userId, id);
        addressRepository.clearDefault(userId);
        if (addressRepository.markDefault(userId, id) <= 0) {
            throw new DBException(ErrorCode.INTERNAL_ERROR, "地址设置默认失败！");
        }
    }

    /** 删除地址（先验归属，再按 {userId, id} 删除）。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long userId, Long id) {
        loadOwned(userId, id);
        if (addressRepository.deleteByUserIdAndId(userId, id) <= 0) {
            throw new DBException(ErrorCode.INTERNAL_ERROR, "地址删除失败！");
        }
    }

    // ==================== 内部方法 ====================

    /** 装载并校验归属（不存在 / 非本人 → 业务异常）。 */
    private Address loadOwned(Long userId, Long id) {
        Address address = addressRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.ILLEGAL_REQUEST, "无权操作该地址"));
        address.assertOwnedBy(userId);
        return address;
    }

    /** 请求 → 领域对象（新增用）。 */
    private Address toDomain(AddressReq req) {
        Address address = Address.builder().build();
        merge(address, req);
        return address;
    }

    /** 请求字段 → 领域对象（null 字段由持久化层的 not-null 更新策略跳过，部分更新语义与迁移前一致）。 */
    private void merge(Address address, AddressReq req) {
        address.setReceiver(req.getReceiver());
        address.setPhone(req.getPhone());
        address.setProvince(req.getProvince());
        address.setCity(req.getCity());
        address.setDistrict(req.getDistrict());
        address.setStreet(req.getStreet());
        address.setDetail(req.getDetail());
        address.setDefaulted(req.getDefaulted());
        address.setLabel(req.getLabel());
        address.setLongitude(req.getLongitude());
        address.setLatitude(req.getLatitude());
    }
}
