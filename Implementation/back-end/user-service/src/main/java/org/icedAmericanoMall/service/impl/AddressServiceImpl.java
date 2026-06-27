package org.icedAmericanoMall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.icedAmericanoMall.service.AddressService;
import org.icedAmericanoMall.convert.AddressConverter;
import org.icedAmericanoMall.domain.dto.AddressReq;
import org.icedAmericanoMall.domain.entity.AddressEntity;
import org.icedAmericanoMall.domain.vo.AddressResp;
import org.icedAmericanoMall.mapper.AddressMapper;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.noLazy.common.exception.DBException;
import org.noLazy.common.utils.BeanUtils;
import org.noLazy.common.utils.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AddressServiceImpl extends ServiceImpl<AddressMapper, AddressEntity> implements AddressService {

    private final AddressConverter addressConverter;

    public AddressServiceImpl(AddressConverter addressConverter) {
        this.addressConverter = addressConverter;
    }

    @Override
    public List<AddressResp> getListByCurrentUser(Long currentUserId) {
        List<AddressEntity> addressList = lambdaQuery().eq(AddressEntity::getUserId, currentUserId).list();
        return addressConverter.mapList(addressList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addAddress(AddressReq req) {
        Long currentUserId = UserContext.getUser();
        AddressEntity addressEntity = BeanUtils.copyBean(req, AddressEntity.class);
        addressEntity.setUserId(currentUserId);
        boolean save = save(addressEntity);
        if (!save) {
            throw new DBException(ErrorCode.INTERNAL_ERROR, "地址保存失败！");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        Long currentUserId = UserContext.getUser();
        AddressEntity address = lambdaQuery().eq(AddressEntity::getId, id).one();
        if (address == null || !address.getUserId().equals(currentUserId)) {
            throw new BizException(ErrorCode.ILLEGAL_REQUEST, "无权操作该地址");
        }
        lambdaUpdate()
                .eq(AddressEntity::getUserId, currentUserId)
                .set(AddressEntity::getDefaulted, false)
                .update();
        boolean update = lambdaUpdate()
                .eq(AddressEntity::getId, id)
                .set(AddressEntity::getDefaulted, true)
                .update();
        if (!update) {
            throw new DBException(ErrorCode.INTERNAL_ERROR, "地址设置默认失败！");
        }
    }

    @Override
    public AddressResp getAddressById(Long id) {
        AddressEntity addressEntity = lambdaQuery().eq(AddressEntity::getId, id).one();
        if (addressEntity == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND, "地址不存在");
        }
        return addressConverter.map(addressEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAddress(Long id, AddressReq req) {
        Long currentUserId = UserContext.getUser();
        AddressEntity existing = lambdaQuery().eq(AddressEntity::getId, id).one();
        if (existing == null || !existing.getUserId().equals(currentUserId)) {
            throw new BizException(ErrorCode.ILLEGAL_REQUEST, "无权操作该地址");
        }
        AddressEntity addressEntity = BeanUtils.copyBean(req, AddressEntity.class);
        addressEntity.setId(id);
        boolean update = updateById(addressEntity);
        if (!update) {
            throw new DBException(ErrorCode.INTERNAL_ERROR, "地址修改失败！");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long id) {
        Long currentUserId = UserContext.getUser();
        AddressEntity existing = lambdaQuery().eq(AddressEntity::getId, id).one();
        if (existing == null || !existing.getUserId().equals(currentUserId)) {
            throw new BizException(ErrorCode.ILLEGAL_REQUEST, "无权操作该地址");
        }
        boolean delete = removeById(id);
        if (!delete) {
            throw new DBException(ErrorCode.INTERNAL_ERROR, "地址删除失败！");
        }
    }
}
