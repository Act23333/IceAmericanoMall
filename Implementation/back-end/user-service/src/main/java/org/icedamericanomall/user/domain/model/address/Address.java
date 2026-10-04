package org.icedamericanomall.user.domain.model.address;

import lombok.Builder;
import lombok.Data;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 收货地址聚合根（纯 POJO）。
 *
 * <p>业务规则：地址归属校验（越权防范 —— 只能操作自己的地址）、默认地址唯一性
 * （同一用户同时最多一条默认地址，由仓储的批次更新保证）。
 */
@Data
@Builder
public class Address {

    /** 技术主键（地址业务键 = userId + id，范围查询均在 WHERE 中带 userId 兜底） */
    private Long id;

    /** 所属用户 ID（跨服务 user_id 语义，技术主键） */
    private Long userId;

    private String receiver;

    private String phone;

    private String province;

    private String city;

    private String district;

    private String street;

    private String detail;

    /** 是否默认地址 */
    private Boolean defaulted;

    /** 地址标签（如"家"、"公司"） */
    private String label;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ==================== 业务方法 ====================

    /**
     * 断言地址归属当前用户（越权防范）。
     *
     * @param currentUserId 当前登录用户 ID
     * @throws BizException 非本人地址
     */
    public void assertOwnedBy(Long currentUserId) {
        if (currentUserId == null || !Objects.equals(this.userId, currentUserId)) {
            throw new BizException(ErrorCode.ILLEGAL_REQUEST, "无权操作该地址");
        }
    }

    /** 标记为默认地址。 */
    public void markDefault() {
        this.defaulted = true;
        this.updateTime = LocalDateTime.now();
    }

    /** 取消默认标记。 */
    public void unmarkDefault() {
        this.defaulted = false;
        this.updateTime = LocalDateTime.now();
    }
}
