package org.icedamericanomall.user.infrastructure.persistence;


import org.icedamericanomall.user.domain.entity.User;
import org.icedamericanomall.common.enums.IdentityTypeEnum;

public interface UserRepository {

    Long findByIdentity(IdentityTypeEnum type, String identifier);

    void save(User user);
}
