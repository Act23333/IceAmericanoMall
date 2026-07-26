package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.dto.AddressReq;
import org.icedAmericanoMall.domain.entity.AddressEntity;
import org.icedAmericanoMall.domain.vo.AddressResp;

import java.util.List;


public interface AddressService extends IService<AddressEntity> {

    List<AddressResp> getListByCurrentUser(Long currentUserId);

    void addAddress(AddressReq req);

    void setDefault(Long id);

    AddressResp getAddressById(Long id);

    void updateAddress(Long id, AddressReq req);

    void deleteAddress(Long id);
}
