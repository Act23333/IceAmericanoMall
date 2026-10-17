package org.icedamericanomall.user.app.address;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
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

    /**
     * 查询地址详情（**不校验归属**，仅供集群内部 Feign 快照使用 —— 见 {@code /internal/address/{id}}）。
     *
     * <p><b>错误码归位（批次 ①）</b>：地址不存在曾借 {@code USER_NOT_FOUND(1102, "地址不存在")} ——
     * 用一个「用户」码承载「地址」资源，调用方无法按码分辨实体。
     * 现改为 {@link ErrorCode#NO_RESOURCE_FOUND}（1103 / HTTP 404，同为 not-found 家族），
     * <b>不新增</b> {@code ADDRESS_NOT_FOUND}：新增码会破坏前后端 99:99 逐条对齐门禁。
     */
    public AddressResp getById(Long id) {
        return addressRepository.findById(id)
                .map(AddressResp::from)
                .orElseThrow(() -> new BizException(ErrorCode.NO_RESOURCE_FOUND, "地址不存在"));
    }

    /**
     * 查询**本人**地址详情（越权读防护：先验归属，非本人一律拒绝）。
     *
     * <p>公开端点 {@code GET /api/user/address/{id}} 必须走本方法；
     * {@link #getById(Long)} 不得暴露给公开端点（否则任意登录用户可读他人 PII）。
     */
    public AddressResp getOwnedByUser(Long userId, Long id) {
        return AddressResp.from(loadOwned(userId, id));
    }

    /**
     * 修改地址（先验归属，再按 {userId, id} 更新）。
     *
     * <p><b>0 行 ≠ 服务器错误（批次 ①，三个写方法同口径）</b>：{@code loadOwned} 已取到该行，
     * 故「按 {userId, id} 更新影响 0 行」只可能有一个原因 ——
     * <b>并发删除</b>（进方法时还在、落更新时已被另一请求删掉）。这属「资源不存在」，
     * 此前被承载成 {@code DBException(INTERNAL_ERROR)}（<b>500</b>），把一次正常的并发收敛
     * 报成了服务端故障（会误触告警、且前端只能提示「服务异常」）。
     * 现按 {@code 09-constraints.md} §三·六 归位为 <b>404 + {@code NO_RESOURCE_FOUND}</b>。
     *
     * <p><b>为什么不走幂等 200</b>：更新/删除的目标已消失，若回 200 等于告诉客户端
     * 「你的修改生效了」—— 用户刷新后地址仍在（或更糟：默认地址被清掉却没设新的）。
     * 只有<b>纯读</b>场景才适合 fail-open；写路径的存在性失败必须可见。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateAddress(Long userId, Long id, AddressReq req) {
        Address existing = loadOwned(userId, id);
        merge(existing, req);
        if (addressRepository.updateByUserIdAndId(existing) <= 0) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "地址不存在");
        }
    }

    /** 设为默认地址（先清旧默认，再标记新默认，同一事务）；0 行口径同 {@link #updateAddress}。 */
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long id) {
        loadOwned(userId, id);
        addressRepository.clearDefault(userId);
        if (addressRepository.markDefault(userId, id) <= 0) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "地址不存在");
        }
    }

    /** 删除地址（先验归属，再按 {userId, id} 删除）；0 行口径同 {@link #updateAddress}。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long userId, Long id) {
        loadOwned(userId, id);
        if (addressRepository.deleteByUserIdAndId(userId, id) <= 0) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "地址不存在");
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
