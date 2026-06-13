package org.icedAmericanoMall.domain.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * @ClassName: UserLoginVo
 * @Description: TODO
 * @Author: noLazy
 * @Date: 2026/3/25 16:42
 * @Version: 1.0.0
 * @ProjectName: IcedAmericanoMall
 * @Package: org.icedAmericanoMall.domain.vo
 */

@Getter
@Setter
public class LoginVO {
    private String token;
    private Long userId;
    private String username;
}
