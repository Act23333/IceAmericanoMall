package org.icedAmericanoMall.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UpdateProfileReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 头像 URL */
    private String avatar;

    /** 昵称（映射到 username） */
    private String nickname;
}
