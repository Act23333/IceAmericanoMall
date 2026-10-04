package org.icedamericanomall.logistics.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.logistics.domain.model.logistics.Package;
import org.icedamericanomall.logistics.domain.repository.PackageRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 物流包裹仓储实现 —— MyBatis-Plus 访问 {@code order_logistics} 表，PO ↔ 聚合转换内聚于此。
 */
@Repository
@RequiredArgsConstructor
public class PackageRepositoryImpl implements PackageRepository {

    private final OrderLogisticsMapper orderLogisticsMapper;

    @Override
    public List<Package> findByOrderId(Long orderId) {
        return orderLogisticsMapper.selectList(new LambdaQueryWrapper<OrderLogisticsPO>()
                        .eq(OrderLogisticsPO::getOrderId, orderId)
                        .orderByAsc(OrderLogisticsPO::getPackageNo))
                .stream()
                .map(PackageRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    public Optional<Package> findByOrderIdAndPackageNo(Long orderId, Integer packageNo) {
        OrderLogisticsPO po = orderLogisticsMapper.selectOne(new LambdaQueryWrapper<OrderLogisticsPO>()
                .eq(OrderLogisticsPO::getOrderId, orderId)
                .eq(OrderLogisticsPO::getPackageNo, Package.normalizePackageNo(packageNo)));
        return po == null ? Optional.empty() : Optional.of(toDomain(po));
    }

    @Override
    public boolean existsByOrderIdAndPackageNo(Long orderId, Integer packageNo) {
        Long count = orderLogisticsMapper.selectCount(new LambdaQueryWrapper<OrderLogisticsPO>()
                .eq(OrderLogisticsPO::getOrderId, orderId)
                .eq(OrderLogisticsPO::getPackageNo, Package.normalizePackageNo(packageNo)));
        return count != null && count > 0;
    }

    @Override
    public void save(Package pkg) {
        orderLogisticsMapper.insert(toPO(pkg));
    }

    @Override
    public void update(Package pkg) {
        orderLogisticsMapper.updateById(toPO(pkg));
    }

    private static Package toDomain(OrderLogisticsPO po) {
        return Package.rehydrate(po.getId(), po.getOrderId(), po.getPackageNo(),
                po.getLogisticsNumber(), po.getLogisticsCompany(), po.getContact(), po.getMobile(),
                po.getProvince(), po.getCity(), po.getDistrict(), po.getStreet(), po.getDetail(),
                po.getStatus(), po.getCreateTime(), po.getUpdateTime());
    }

    private static OrderLogisticsPO toPO(Package pkg) {
        OrderLogisticsPO po = new OrderLogisticsPO();
        po.setId(pkg.getId());
        po.setOrderId(pkg.getOrderId());
        po.setPackageNo(pkg.getPackageNo());
        po.setLogisticsNumber(pkg.getLogisticsNumber());
        po.setLogisticsCompany(pkg.getLogisticsCompany());
        po.setContact(pkg.getContact());
        po.setMobile(pkg.getMobile());
        po.setProvince(pkg.getProvince());
        po.setCity(pkg.getCity());
        po.setDistrict(pkg.getDistrict());
        po.setStreet(pkg.getStreet());
        po.setDetail(pkg.getDetail());
        po.setStatus(pkg.getStatus());
        return po;
    }

    /** 供转换器/测试引用状态码口径（enum↔int 双向）。 */
}
