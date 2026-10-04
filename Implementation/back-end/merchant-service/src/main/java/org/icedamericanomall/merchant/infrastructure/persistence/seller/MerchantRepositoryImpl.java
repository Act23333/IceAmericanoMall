package org.icedamericanomall.merchant.infrastructure.persistence.seller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.merchant.domain.enums.MerchantStatusEnum;
import org.icedamericanomall.merchant.domain.model.merchant.Merchant;
import org.icedamericanomall.merchant.domain.repository.MerchantRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 商家仓储实现（基础设施层）。
 *
 * <p>业务唯一键 {@code userId}（{@code uk_user_id}，一个用户至多一个商家）：
 * 并发重复申请的键冲突在此翻译为 {@code USER_ALREADY_EXISTS} 业务异常，
 * 消除"先查后插"竞态下的裸 500。
 */
@Repository
@RequiredArgsConstructor
public class MerchantRepositoryImpl implements MerchantRepository {

    private final MerchantMapper sellerMapper;

    @Override
    public Optional<Merchant> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(MerchantConverter.toDomain(sellerMapper.selectOne(
                new LambdaQueryWrapper<MerchantPO>().eq(MerchantPO::getUserId, userId).last("LIMIT 1"))));
    }

    @Override
    public Optional<Merchant> findById(Long id) {
        return Optional.ofNullable(MerchantConverter.toDomain(sellerMapper.selectById(id)));
    }

    @Override
    public Merchant insert(Merchant merchant) {
        MerchantPO dataObject = MerchantConverter.toDataObject(merchant);
        dataObject.setId(null);
        try {
            sellerMapper.insert(dataObject);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.USER_ALREADY_EXISTS, "已申请过商家");
        }
        return MerchantConverter.toDomain(dataObject);
    }

    @Override
    public int updateStatus(Merchant merchant) {
        return sellerMapper.update(null, new LambdaUpdateWrapper<MerchantPO>()
                .eq(MerchantPO::getId, merchant.getId())
                .set(MerchantPO::getStatus,
                        merchant.getStatus() == null ? null : merchant.getStatus().getCode())
                .set(MerchantPO::getUpdateTime, merchant.getUpdateTime()));
    }

    @Override
    public List<Merchant> findPageByStatus(MerchantStatusEnum status, int offset, int size) {
        // 保持迁移前排序语义（无显式 ORDER BY），避免列表顺序变化影响响应
        return sellerMapper.selectList(new LambdaQueryWrapper<MerchantPO>()
                        .eq(MerchantPO::getStatus, status == null ? null : status.getCode())
                        .last("LIMIT " + size + " OFFSET " + offset))
                .stream()
                .map(MerchantConverter::toDomain)
                .toList();
    }

    @Override
    public long countByStatus(MerchantStatusEnum status) {
        return sellerMapper.selectCount(new LambdaQueryWrapper<MerchantPO>()
                .eq(MerchantPO::getStatus, status == null ? null : status.getCode()));
    }
}
