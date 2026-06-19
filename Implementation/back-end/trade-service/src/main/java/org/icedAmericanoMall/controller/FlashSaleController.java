package org.icedAmericanoMall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.icedAmericanoMall.domain.entity.FlashSaleEntity;
import org.icedAmericanoMall.mapper.FlashSaleMapper;
import org.noLazy.common.domain.Result;
import org.noLazy.common.enums.ErrorCode;
import org.noLazy.common.exception.BizException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flash")
@RequiredArgsConstructor
public class FlashSaleController {

    private final FlashSaleMapper flashSaleMapper;

    /** 当前秒杀活动列表 */
    @GetMapping
    public Result<List<FlashSaleEntity>> list() {
        LocalDateTime now = LocalDateTime.now();
        return Result.ok(flashSaleMapper.selectList(
                new LambdaQueryWrapper<FlashSaleEntity>()
                        .le(FlashSaleEntity::getStartTime, now)
                        .ge(FlashSaleEntity::getEndTime, now)
                        .eq(FlashSaleEntity::getStatus, 2)));
    }

    /** 秒杀抢购 — 乐观锁扣库存 */
    @PostMapping("/buy")
    public Result<?> buy(@RequestParam Long flashId) {
        FlashSaleEntity fs = flashSaleMapper.selectById(flashId);
        if (fs == null) throw new BizException(ErrorCode.USER_NOT_FOUND, "秒杀活动不存在");
        if (fs.getStatus() != 2) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "秒杀未开始或已结束");
        if (fs.getSoldCount() >= fs.getStock()) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "已售罄");

        fs.setSoldCount(fs.getSoldCount() + 1);
        int updated = flashSaleMapper.updateById(fs);
        if (updated == 0) throw new BizException(ErrorCode.BUSINESS_EXECUTION_EXCEPTION, "抢购失败，请重试");

        return Result.ok(Map.of("success", true, "flashPrice", fs.getFlashPrice(), "productId", fs.getProductId()));
    }
}
