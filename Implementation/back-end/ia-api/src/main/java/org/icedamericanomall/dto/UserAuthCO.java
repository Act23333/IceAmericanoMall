package org.icedamericanomall.dto;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.IdentityTypeEnum;
import org.icedamericanomall.common.model.EncryptedIdentifier;

/**
 * @ClassName: UserAuthCO
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/9/24 23:34
 * @Version: 1.0.0
 * @ProjectName: back-end
 * @Package: org.icedamericanomall.dto
 */
@Builder
@Data
public class UserAuthCO {
    private String identifier;

    private IdentityTypeEnum identityType;
}
