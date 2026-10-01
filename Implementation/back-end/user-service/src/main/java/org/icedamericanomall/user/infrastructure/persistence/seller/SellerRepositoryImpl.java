package org.icedamericanomall.user.infrastructure.persistence.seller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.model.Seller;
import org.icedamericanomall.user.domain.model.SellerStatusEnum;
import org.icedamericanomall.user.domain.repository.SellerRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商家仓储实现（基础设施层）。
 *
 * <p>业务唯一键 {@code userId}（一个用户至多一个商家）；
 * 更新店铺信息只写变更字段（null 忽略），不整行覆盖。
 */
@Repository
@RequiredArgsConstructor
public class SellerRepositoryImpl implements SellerRepository {

    private final SellerMapper sellerMapper;

    @Override
    public Optional<Seller> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SellerConverter.toDomain(sellerMapper.selectOne(
                new LambdaQueryWrapper<SellerDO>().eq(SellerDO::getUserId, userId).last("LIMIT 1"))));
    }

    @Override
    public Optional<Seller> findActiveByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SellerConverter.toDomain(sellerMapper.selectOne(
                new LambdaQueryWrapper<SellerDO>()
                        .eq(SellerDO::getUserId, userId)
                        .eq(SellerDO::getStatus, SellerStatusEnum.NORMAL.getCode())
                        .last("LIMIT 1"))));
    }

    @Override
    public Optional<Seller> findById(Long id) {
        return Optional.ofNullable(SellerConverter.toDomain(sellerMapper.selectById(id)));
    }

    @Override
    public Seller insert(Seller seller) {
        SellerDO dataObject = SellerConverter.toDataObject(seller);
        dataObject.setId(null);
        sellerMapper.insert(dataObject);
        return SellerConverter.toDomain(dataObject);
    }

    @Override
    public int updateStatusById(Long id, SellerStatusEnum status) {
        return sellerMapper.update(null, new LambdaUpdateWrapper<SellerDO>()
                .eq(SellerDO::getId, id)
                .set(SellerDO::getStatus, status == null ? null : status.getCode()));
    }

    @Override
    public int updateShopInfo(Seller seller) {
        LambdaUpdateWrapper<SellerDO> update = new LambdaUpdateWrapper<SellerDO>()
                .eq(SellerDO::getId, seller.getId());
        if (seller.getShopName() != null) update.set(SellerDO::getShopName, seller.getShopName());
        if (seller.getShopLogo() != null) update.set(SellerDO::getShopLogo, seller.getShopLogo());
        if (seller.getContactPhone() != null) update.set(SellerDO::getContactPhone, seller.getContactPhone());
        if (seller.getProvince() != null) update.set(SellerDO::getProvince, seller.getProvince());
        if (seller.getCity() != null) update.set(SellerDO::getCity, seller.getCity());
        if (seller.getDistrict() != null) update.set(SellerDO::getDistrict, seller.getDistrict());
        if (seller.getDetailAddress() != null) update.set(SellerDO::getDetailAddress, seller.getDetailAddress());
        return sellerMapper.update(null, update);
    }

    @Override
    public List<Seller> findPageByStatus(SellerStatusEnum status, int offset, int size) {
        // 保持迁移前排序语义（无显式 ORDER BY），避免列表顺序变化影响响应
        return sellerMapper.selectList(new LambdaQueryWrapper<SellerDO>()
                        .eq(SellerDO::getStatus, status == null ? null : status.getCode())
                        .last("LIMIT " + size + " OFFSET " + offset))
                .stream()
                .map(SellerConverter::toDomain)
                .toList();
    }

    @Override
    public long countByStatus(SellerStatusEnum status) {
        return sellerMapper.selectCount(new LambdaQueryWrapper<SellerDO>()
                .eq(SellerDO::getStatus, status == null ? null : status.getCode()));
    }
}
