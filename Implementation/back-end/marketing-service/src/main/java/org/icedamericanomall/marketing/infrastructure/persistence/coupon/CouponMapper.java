package org.icedamericanomall.marketing.infrastructure.persistence.coupon;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 优惠券模板 Mapper —— 入参/出参均为 PO。
 */
@Mapper
public interface CouponMapper extends BaseMapper<CouponPO> {

    /**
     * 按业务唯一键取券模板（仓储口径：一律按 {@code coupon_id} 定位，禁止按技术主键 id 查询）。
     *
     * @param couponId 业务券 ID（16 字节原始形态，ADR-050：调用方经 IdGenerator.toBytesOrNull 转换——外部入参非法按未命中处理）
     * @return 券模板，不存在返回 null
     */
    @Select("SELECT * FROM coupon WHERE coupon_id = #{couponId} LIMIT 1")
    CouponPO selectByCouponId(@Param("couponId") byte[] couponId);
}
