package org.icedAmericanoMall.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class UpdateProfileReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 头像 URL */
    private String avatar;
    @JsonInclude(JsonInclude.Include.NON_NULL)

    /** 昵称（映射到 username） */
    private String nickname;

    // 布尔标记是否更新该字段（或者依靠 null 语义）
//    @JsonProperty("update_nickname")
//    private boolean updateNickname;

    //明确列出要更新哪些字段，用于区分是否传值还是不传或者传null
    private List<String> updateMask;

}
