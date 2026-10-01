package org.icedamericanomall.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.user.domain.dto.AddressReq;
import org.icedamericanomall.user.domain.entity.AddressEntity;
import org.icedamericanomall.user.domain.vo.AddressResp;

import java.util.List;


public interface AddressService extends IService<AddressEntity> {

    List<AddressResp> getListByCurrentUser(Long currentUserId);

    void addAddress(AddressReq req);

    void setDefault(Long id);

    AddressResp getAddressById(Long id);

    void updateAddress(Long id, AddressReq req);

    void deleteAddress(Long id);
}
