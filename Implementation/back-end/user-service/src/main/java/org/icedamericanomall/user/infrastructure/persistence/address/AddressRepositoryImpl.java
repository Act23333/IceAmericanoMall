package org.icedamericanomall.user.infrastructure.persistence.address;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.user.domain.model.address.Address;
import org.icedamericanomall.user.domain.repository.AddressRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 收货地址仓储实现（基础设施层）。
 *
 * <p>业务键 = {@code userId + id}：更新 / 删除 / 标默认均在 WHERE 中携带 userId，
 * 与领域实体的归属断言形成双层越权防范；不存在按裸 id 的操作。
 */
@Repository
@RequiredArgsConstructor
public class AddressRepositoryImpl implements AddressRepository {

    private final AddressMapper addressMapper;

    @Override
    public List<Address> findByUserId(Long userId) {
        // 保持迁移前排序语义（无显式 ORDER BY → 主键顺序），避免列表顺序变化影响前端
        return addressMapper.selectList(new LambdaQueryWrapper<AddressPO>()
                        .eq(AddressPO::getUserId, userId))
                .stream()
                .map(AddressConverter::toDomain)
                .toList();
    }

    @Override
    public Optional<Address> findById(Long id) {
        return Optional.ofNullable(AddressConverter.toDomain(addressMapper.selectById(id)));
    }

    @Override
    public Address insert(Address address) {
        AddressPO dataObject = AddressConverter.toDataObject(address);
        dataObject.setId(null);
        addressMapper.insert(dataObject);
        return AddressConverter.toDomain(dataObject);
    }

    @Override
    public int updateByUserIdAndId(Address address) {
        AddressPO dataObject = AddressConverter.toDataObject(address);
        return addressMapper.update(dataObject, new LambdaUpdateWrapper<AddressPO>()
                .eq(AddressPO::getUserId, address.getUserId())
                .eq(AddressPO::getId, address.getId()));
    }

    @Override
    public int deleteByUserIdAndId(Long userId, Long id) {
        return addressMapper.delete(new LambdaQueryWrapper<AddressPO>()
                .eq(AddressPO::getUserId, userId)
                .eq(AddressPO::getId, id));
    }

    @Override
    public int clearDefault(Long userId) {
        return addressMapper.update(null, new LambdaUpdateWrapper<AddressPO>()
                .eq(AddressPO::getUserId, userId)
                .eq(AddressPO::getDefaulted, true)
                .set(AddressPO::getDefaulted, false)
                .set(AddressPO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int markDefault(Long userId, Long id) {
        return addressMapper.update(null, new LambdaUpdateWrapper<AddressPO>()
                .eq(AddressPO::getUserId, userId)
                .eq(AddressPO::getId, id)
                .set(AddressPO::getDefaulted, true)
                .set(AddressPO::getUpdateTime, LocalDateTime.now()));
    }
}
