package org.icedamericanomall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @ClassName: RegisterFormDTO
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/4/1 22:30
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.noLazy.common.dto
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRespDTO {
    private Long userId;
    private String username;
}