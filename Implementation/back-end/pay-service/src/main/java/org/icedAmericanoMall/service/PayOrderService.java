package org.icedAmericanoMall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedAmericanoMall.domain.entity.PayOrderEntity;

import java.util.Map;

public interface PayOrderService extends IService<PayOrderEntity> {

    PayOrderEntity initiatePayment(String orderNo, Long userId);

    void handleCallback(Map<String, String> params);

    PayOrderEntity queryStatus(String payOrderNo);
}
