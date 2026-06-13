package org.noLazy.common.dto;

import lombok.Data;

@Data
public class Geetest4ValidateRequest {
    private String lotNumber;      // 验证流水号
    private String captchaOutput;  // 验证输出信息
    private String passToken;      // 验证通过标识
    private String genTime;        // 验证通过时间戳（毫秒）
    // 注意：captchaId 和 signToken 由后端生成，不从前端接收
}
