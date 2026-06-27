package org.icedAmericanoMall.domain.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.icedAmericanoMall.domain.enums.CredentialTypeEnum;
import org.icedAmericanoMall.domain.enums.IdentityTypeEnum;

import java.io.Serial;
import java.io.Serializable;

@Data
public class LoginReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "identityType不能为空")
    private IdentityTypeEnum identityType;

    @NotBlank(message = "account不能为空")
    private String account;

    @NotNull(message = "credentialType不能为空")
    private CredentialTypeEnum credentialType;

    @NotBlank(message = "credential不能为空")
    private String credential;
}
