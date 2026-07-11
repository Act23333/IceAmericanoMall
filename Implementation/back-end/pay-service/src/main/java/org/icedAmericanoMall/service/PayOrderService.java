package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.PayOrderEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付单领域服务 —— 仅负责支付单的原子读写与状态流转，不做跨服务编排（编排见 PayManager）。
 */
public interface PayOrderService extends IService<PayOrderEntity> {

    PayOrderEntity getByBizOrderNo(String bizOrderNo);

    PayOrderEntity getByPayOrderNo(String payOrderNo);

    /** 创建"待支付"支付单并落库。 */
    PayOrderEntity createPending(String bizOrderNo, Long userId, int amount, String qrCodeUrl);

    /** 标记支付成功（幂等由调用方保证）。 */
    void markSuccess(PayOrderEntity payOrder, String resultCode);

    /** 查询已过期的"待支付"支付单。 */
    List<PayOrderEntity> listTimeout(LocalDateTime cutoff);

    /** 标记支付单为超时取消。 */
    void markTimeoutCancel(Long payOrderId);
}
