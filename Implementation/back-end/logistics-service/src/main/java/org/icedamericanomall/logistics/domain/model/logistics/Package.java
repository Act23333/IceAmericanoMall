package org.icedamericanomall.logistics.domain.model.logistics;

import lombok.Getter;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.logistics.domain.enums.LogisticsStatusEnum;

/**
 * 物流包裹聚合根 —— 一个订单包裹的一条物流记录（V5.6 一单多包裹）。
 *
 * <p>业务规则内聚：状态单调迁移（PENDING→SHIPPED→DELIVERED↔RETURNED）、同状态重放幂等、
 * 非法迁移拒绝；包裹号缺省 1（单包裹语义兼容）。持久化形态见
 * {@code infrastructure.persistence.OrderLogisticsPO}。
 */
@Getter
public class Package {

    /** 包裹号缺省值：单包裹语义（存量兼容，与 DDL 的 DEFAULT 1 对齐）。 */
    public static final int DEFAULT_PACKAGE_NO = 1;

    private Long id;
    private Long orderId;
    private Integer packageNo;
    private String logisticsNumber;
    private String logisticsCompany;
    private String contact;
    private String mobile;
    private String province;
    private String city;
    private String district;
    private String street;
    private String detail;
    /** 物流状态码（{@link LogisticsStatusEnum} 的 code）。 */
    private Integer status;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;

    private Package() {
    }

    /**
     * 发货事件创建包裹（internal 创建链路）：包裹号缺省 1，状态缺省待揽收。
     */
    public static Package create(Long orderId, Integer packageNo, String logisticsNumber,
                                 String logisticsCompany, String contact, String mobile,
                                 String province, String city, String district,
                                 String street, String detail) {
        Package pkg = new Package();
        pkg.orderId = orderId;
        pkg.packageNo = normalizePackageNo(packageNo);
        pkg.logisticsNumber = logisticsNumber;
        pkg.logisticsCompany = logisticsCompany;
        pkg.contact = contact;
        pkg.mobile = mobile;
        pkg.province = province;
        pkg.city = city;
        pkg.district = district;
        pkg.street = street;
        pkg.detail = detail;
        pkg.status = LogisticsStatusEnum.PENDING.getCode();
        return pkg;
    }

    /** 仓储重建（读模型恢复）。 */
    public static Package rehydrate(Long id, Long orderId, Integer packageNo, String logisticsNumber,
                                    String logisticsCompany, String contact, String mobile,
                                    String province, String city, String district,
                                    String street, String detail, Integer status,
                                    java.time.LocalDateTime createTime, java.time.LocalDateTime updateTime) {
        Package pkg = new Package();
        pkg.id = id;
        pkg.orderId = orderId;
        pkg.packageNo = packageNo;
        pkg.logisticsNumber = logisticsNumber;
        pkg.logisticsCompany = logisticsCompany;
        pkg.contact = contact;
        pkg.mobile = mobile;
        pkg.province = province;
        pkg.city = city;
        pkg.district = district;
        pkg.street = street;
        pkg.detail = detail;
        pkg.status = status;
        pkg.createTime = createTime;
        pkg.updateTime = updateTime;
        return pkg;
    }

    /**
     * 状态迁移（单调 + 幂等）：同状态重放为空操作（返回 false）；非法迁移抛 ORDER_STATUS_INVALID。
     *
     * @param target 目标状态（非法状态码由应用层先行解析为 PARAM_ERROR）
     * @return 是否发生实际变更（false = 同状态幂等空操作，调用方无需落库）
     */
    public boolean transitTo(LogisticsStatusEnum target) {
        LogisticsStatusEnum current = LogisticsStatusEnum.of(status);
        if (current == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "物流单当前状态非法: " + status);
        }
        if (current == target) {
            return false; // 同状态重放：幂等空操作
        }
        if (!current.canTransitTo(target)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID,
                    "非法物流状态迁移: " + current.getDesc() + " → " + target.getDesc());
        }
        status = target.getCode();
        return true;
    }

    /** 包裹号归一：{@code null} 按 1（单包裹语义）处理。 */
    public static int normalizePackageNo(Integer packageNo) {
        return packageNo == null ? DEFAULT_PACKAGE_NO : packageNo;
    }
}
