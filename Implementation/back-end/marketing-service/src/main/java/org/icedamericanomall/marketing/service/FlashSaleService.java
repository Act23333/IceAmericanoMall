package org.icedamericanomall.marketing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.icedamericanomall.marketing.domain.dto.FlashBuyResult;
import org.icedamericanomall.marketing.domain.entity.FlashSaleEntity;
import java.util.List;

public interface FlashSaleService extends IService<FlashSaleEntity> {
    List<FlashSaleEntity> listActive();

    /** V4.1: 秒杀购买 — Redis Lua 预扣 + 调用 trade-service 创建真实订单 */
    FlashBuyResult buy(Long flashId, Long addressId);
}
