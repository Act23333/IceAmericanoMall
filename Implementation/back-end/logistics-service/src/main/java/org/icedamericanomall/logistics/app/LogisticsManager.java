package org.icedamericanomall.logistics.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.OrderClient;
import org.icedamericanomall.dto.CreateLogisticsCO;
import org.icedamericanomall.dto.OrderSummaryCO;
import org.icedamericanomall.logistics.adapter.assembler.LogisticsConverter;
import org.icedamericanomall.logistics.adapter.response.LogisticsVO;
import org.icedamericanomall.logistics.domain.enums.LogisticsStatusEnum;
import org.icedamericanomall.logistics.domain.model.logistics.Package;
import org.icedamericanomall.logistics.domain.repository.PackageRepository;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.common.exception.ForbiddenException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 物流编排（Application 层）—— 物流 CRUD 编排 + 跨服务（trade-service）归属校验。
 * 事务内禁止 Feign：本类方法均不加 {@code @Transactional}，Feign 校验先于领域写入发生。
 *
 * <p><b>V5.6（F3 拆单发货）</b>：对外查询返回<b>包裹列表</b>（一订单多包裹，按 packageNo 升序）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogisticsManager {

    private final PackageRepository packageRepository;
    private final LogisticsConverter logisticsConverter;
    private final OrderClient orderClient;

    /**
     * 查询订单全部包裹物流并校验归属：仅订单所属用户可查看（返回包裹列表，按 packageNo 升序）。
     *
     * @throws BizException       订单不存在（ORDER_NOT_FOUND）/ 物流不存在（LOGISTICS_NOT_FOUND）
     * @throws ForbiddenException 订单不属于当前用户（FORBIDDEN）
     */
    public List<LogisticsVO> getOwnedLogistics(Long orderId, Long userId) {
        requireOrderOwnedBy(orderId, userId);
        return packageRepository.findByOrderId(orderId).stream()
                .map(logisticsConverter::toVO)
                .toList();
    }

    /**
     * 发货事件入账（internal 调用）：同一 (订单, 包裹号) 重复事件幂等跳过；
     * 否则经聚合工厂创建（包裹号缺省 1、状态缺省待揽收）后落库。
     */
    public void createLogistics(CreateLogisticsCO co) {
        int packageNo = Package.normalizePackageNo(co.getPackageNo());
        if (packageRepository.existsByOrderIdAndPackageNo(co.getOrderId(), packageNo)) {
            log.info("包裹物流记录已存在，幂等跳过创建: orderId={}, packageNo={}",
                    co.getOrderId(), Package.normalizePackageNo(co.getPackageNo()));
            return;
        }
        Package pkg = Package.create(co.getOrderId(), co.getPackageNo(), co.getLogisticsNumber(),
                co.getLogisticsCompany(), co.getContact(), co.getMobile(),
                co.getProvince(), co.getCity(), co.getDistrict(), co.getStreet(), co.getDetail());
        packageRepository.save(pkg);
    }

    /**
     * 更新指定包裹的物流状态（internal 调用）：非法状态码 PARAM_ERROR；不存在 LOGISTICS_NOT_FOUND；
     * 迁移合法性由聚合内聚校验。
     */
    public void updateStatus(Long orderId, Integer packageNo, Integer status) {
        LogisticsStatusEnum target = LogisticsStatusEnum.of(status);
        if (target == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "非法物流状态: " + status);
        }
        Package pkg = packageRepository.findByOrderIdAndPackageNo(orderId, packageNo)
                .orElseThrow(() -> new BizException(ErrorCode.LOGISTICS_NOT_FOUND, "物流信息不存在"));
        if (pkg.transitTo(target)) {
            packageRepository.update(pkg);
        }
    }

    /** 通过 trade-service 校验订单存在且归属当前用户。 */
    private void requireOrderOwnedBy(Long orderId, Long userId) {
        OrderSummaryCO order = orderClient.getOrderById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在: " + orderId);
        }
        if (order.getUserId() == null || !order.getUserId().equals(userId)) {
            log.warn("越权访问订单物流: orderId={}, ownerId={}, requestUserId={}",
                    orderId, order.getUserId(), userId);
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "无权查看该订单物流");
        }
    }
}
