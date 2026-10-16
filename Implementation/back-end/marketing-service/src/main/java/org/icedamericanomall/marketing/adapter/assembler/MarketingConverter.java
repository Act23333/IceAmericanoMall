package org.icedamericanomall.marketing.adapter.assembler;

import org.icedamericanomall.dto.GroupTeamCO;
import org.icedamericanomall.dto.PromotionCO;
import org.icedamericanomall.marketing.adapter.request.CouponCreateReq;
import org.icedamericanomall.marketing.adapter.request.FlashSaleCreateReq;
import org.icedamericanomall.marketing.adapter.request.FlashSaleUpdateReq;
import org.icedamericanomall.marketing.adapter.request.GroupBuyCreateReq;
import org.icedamericanomall.marketing.adapter.request.GroupBuyUpdateReq;
import org.icedamericanomall.marketing.adapter.request.PromotionCreateReq;
import org.icedamericanomall.marketing.adapter.request.PromotionUpdateReq;
import org.icedamericanomall.marketing.adapter.response.ActivityVO;
import org.icedamericanomall.marketing.adapter.response.CouponVO;
import org.icedamericanomall.marketing.adapter.response.FlashBuyVO;
import org.icedamericanomall.marketing.adapter.response.FlashSaleVO;
import org.icedamericanomall.marketing.adapter.response.GroupBuyActivityVO;
import org.icedamericanomall.marketing.adapter.response.GroupTeamVO;
import org.icedamericanomall.marketing.adapter.response.PointsBalanceVO;
import org.icedamericanomall.marketing.adapter.response.PointsLogVO;
import org.icedamericanomall.marketing.adapter.response.PromotionVO;
import org.icedamericanomall.marketing.adapter.response.SignResultVO;
import org.icedamericanomall.marketing.adapter.response.UserCouponVO;
import org.icedamericanomall.marketing.app.dto.FlashBuyResult;
import org.icedamericanomall.marketing.app.dto.SignResultDTO;
import org.icedamericanomall.marketing.domain.enums.PromotionTypeEnum;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;
import org.icedamericanomall.marketing.domain.model.flash.FlashSale;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupBuyActivity;
import org.icedamericanomall.marketing.domain.model.groupbuy.GroupTeam;
import org.icedamericanomall.marketing.domain.model.points.PointsLog;
import org.icedamericanomall.marketing.domain.model.promotion.Promotion;
import org.icedamericanomall.marketing.domain.service.CouponScope;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * 营销适配层装配器（Assembler）—— 请求对象 / 领域聚合 / 应用层 DTO ↔ 视图对象。
 *
 * <p>出参一律为 VO（聚合不泄漏到适配层之外）；入参 {@link CouponCreateReq} → 领域聚合在此完成，
 * 业务归一化由聚合自身负责（请求对象只做校验绑定）。
 *
 * <p>UNMAPPED 忽略口径：{@code id}/{@code issuedQty}/{@code sellerId}/审计字段由基础设施回填。
 * coupon 的 {@code scopeType}/{@code scopeValues}/{@code stackRule}/{@code stackGroup} 四字段
 * 已与聚合同名同型（V4.3 范围与叠加维度落地），由 MapStruct 按名自动双向映射，无需显式
 * {@code @Mapping}。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MarketingConverter {

    /** 创建请求 → 优惠券聚合（issuedQty 固定置 0，由创建编排负责）。 */
    Coupon toDomain(CouponCreateReq req);

    /**
     * 优惠券聚合 → 视图对象。
     *
     * <p><b>{@code scopeValues} 形态归一出出口</b>：指定 {@link #couponScopeValues(Coupon)}，
     * 把存储形态（DB {@code JSON} 数组串）归一为对外 <b>CSV</b>（契约 §4.10.1：
     * 「对外 CSV 串 / 存储 JSON 数组」；{@code UserCouponVO} 同口径）。
     * 原实现为领域对象原样透传 → 回吐 {@code ["1001","1002"]}，与 {@code UserCouponVO} 不同型。
     */
    @Mapping(target = "scopeValues", expression = "java(couponScopeValues(coupon))")
    CouponVO toVO(Coupon coupon);

    /** 优惠券聚合列表 → 视图对象列表（与 {@link #toVO(Coupon)} 同一 CSV 归一出口）。 */
    @Mapping(target = "scopeValues", expression = "java(couponScopeValues(coupon))")
    CouponVO toCouponVO(Coupon coupon);

    default List<CouponVO> toCouponVOList(List<Coupon> coupons) {
        return coupons == null ? null : coupons.stream().map(this::toCouponVO).toList();
    }

    /**
     * {@code scope_values} 对外形态归一（JSON 数组 → CSV）—— 两个 VO 共用唯一出口。
     *
     * <p>{@code scopeType} 为 {@code null}（缺省=ALL）或 ALL 时返回 {@code null}
     * （契约 §4.10.1 样例的合法形态）。
     */
    default String couponScopeValues(Coupon coupon) {
        return coupon == null ? null : CouponScope.toCsv(coupon.getScopeType(), coupon.getScopeValues());
    }

    /**
     * 用户持券聚合 → 视图对象（仅本体字段）。
     *
     * <p><b>{@code couponId} 必须显式忽略（禁止自动同名映射）</b>：聚合
     * {@code UserCoupon.couponId} 是<b>技术外键</b>（{@code user_coupon.coupon_id BIGINT}
     * → {@code coupon.id}），而 VO {@code UserCouponVO.couponId} 是<b>业务券号</b>
     * （{@code coupon.coupon_id} 的 32 位 hex，ADR-002/ADR-050）—— <b>同名不同义</b>。
     * MapStruct 的内置 {@code Long → String} 转换会把技术主键写成 {@code "5"} 这类值，
     * 静默产出错误券号且与 {@code CouponVO.couponId} 永不相等（前端 join 必失败），
     * 故此处忽略，交由应用层 {@code UserCouponViewAssembler} 从券模板读模型
     * （列表路径）/ 已加载的券模板聚合（领取回执路径）显式回填，取不到则留 null（fail-open）。
     *
     * <p>券面展示字段 {@code name}/{@code value}/{@code min_amount}/{@code discount_type}
     * 同样由 {@code UserCouponViewAssembler} 批量回填（ADR-057）。其中
     * {@code discountType} <b>无需 {@code ignore}</b>：持券聚合 {@link UserCoupon} 上
     * <b>没有该属性</b>（无同名可映射，不构成 ADR-058 的跨层同名异义），MapStruct 不会静默写入
     * —— 但装配器仍然显式赋值，且<b>券模板缺失时必须保持 null</b>（禁止回落为 FIXED/0）。
     * 折扣类型的聚合侧回退口径（{@code CouponPricing.resolveDiscountType} 的
     * {@code type} 回退只服务于<b>折扣计算</b>）不参与 VO 投影。
     *
     * <p>此前按列表批量映射的 {@code toUserCouponVOList} 因回填改为逐元素装配已无调用方，
     * 随该批删除。
     */
    @Mapping(target = "couponId", ignore = true)
    UserCouponVO toVO(UserCoupon userCoupon);

    /**
     * 秒杀聚合 → 视图对象（仅本体字段）。
     *
     * <p>商品字段与业务键需跨聚合回填、{@code progress_percent} 为派生值，二者由
     * {@code FlashSaleViewManager} 在装配后补齐；此前按列表批量映射的
     * {@code toFlashSaleVOList} 因回填改为逐元素装配已无调用方，随本批删除。
     *
     * <p><b>同名异义必须显式 ignore（ADR-058 §4）</b>：{@code FlashSale.spuId}/{@code skuId}
     * 是 <b>技术键</b>（{@code flash_sale.spu_id}/{@code sku_id BIGINT}），而
     * {@code FlashSaleVO.spuId}/{@code skuId} 是 <b>业务键</b> 32-hex —— MapStruct 会把
     * {@code Long 5} 静默写成 {@code "5"}（看似有值、最难排查），故必须 ignore 并由
     * {@code FlashSaleViewManager} 经读模型回填。
     */
    @Mapping(target = "spuId", ignore = true)
    @Mapping(target = "skuId", ignore = true)
    @Mapping(target = "spuName", ignore = true)
    @Mapping(target = "mainImage", ignore = true)
    @Mapping(target = "originalPrice", ignore = true)
    @Mapping(target = "progressPercent", ignore = true)
    FlashSaleVO toVO(FlashSale flashSale);

    /**
     * 秒杀创建请求 → 秒杀聚合（status/soldCount/version 由创建编排推导回填，AD40）。
     *
     * <p><b>同名异义必须显式 ignore（ADR-058 §4）</b>：请求侧 {@code spuId}/{@code skuId}
     * 是<b>业务键</b>（String），聚合侧同名字段是<b>技术键</b>（Long）—— 自动转换会把
     * {@code "0f1e…"} 解析成数字（运行时 {@code NumberFormatException}）或把 {@code Long}
     * 静默写成 {@code "5"}；故必须 ignore，并由 {@code FlashSaleAdminManager} 经读模型
     * 解析后显式赋值。
     */
    @Mapping(target = "spuId", ignore = true)
    @Mapping(target = "skuId", ignore = true)
    FlashSale toDomain(FlashSaleCreateReq req);

    /**
     * 秒杀编辑请求 → 就地合并到已取聚合（V5.7 AD41）。
     *
     * <p>MapStruct 更新语义默认会把源侧 null 覆盖到目标，这里显式
     * {@code NullValuePropertyMappingStrategy.IGNORE} 保证「null-safe 局部更新」契约口径。
     *
     * @param req       编辑请求（字段全可选）
     * @param flashSale 已取出的场次聚合（被合并目标）
     *
     * <p>{@code spuId}/{@code skuId} 同名异义（请求 = 业务键，聚合 = 技术键）→ 显式 ignore，
     * 由 {@code FlashSaleAdminManager} 解析后显式赋值（ADR-058 §4）。
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "spuId", ignore = true)
    @Mapping(target = "skuId", ignore = true)
    void mergeFlashSaleUpdate(FlashSaleUpdateReq req, @MappingTarget FlashSale flashSale);

    /** 促销活动聚合 → 视图对象（AD48 创建回执口径）。 */
    PromotionVO toVO(Promotion promotion);

    /** 促销创建请求 → 促销聚合（status/version/maxDiscount 归一由创建编排负责，AD48）。 */
    Promotion toDomain(PromotionCreateReq req);

    /**
     * 促销编辑请求 → 就地合并到已取聚合（V5.8 AD49）。
     *
     * <p>MapStruct 更新语义默认会把源侧 null 覆盖到目标，这里显式
     * {@code NullValuePropertyMappingStrategy.IGNORE} 保证「null-safe 局部更新」契约口径。
     *
     * @param req       编辑请求（字段全可选）
     * @param promotion 已取出的活动聚合（被合并目标）
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void mergePromotionUpdate(PromotionUpdateReq req, @MappingTarget Promotion promotion);

    /** 促销聚合 → 生效促销契约对象（I44，ia-api {@code PromotionCO}，ADR-043）。 */
    PromotionCO toCO(Promotion promotion);

    /** 促销聚合列表 → 生效促销契约对象列表（I44）。 */
    List<PromotionCO> toPromotionCOList(List<Promotion> promotions);

    /**
     * 拼团创建请求 → 拼团活动聚合（status/version 由创建编排推导回填，AD54）。
     *
     * <p><b>同名异义必须显式 ignore（ADR-058 §4）</b>：请求侧 {@code spuId}/{@code skuId}
     * 是<b>业务键</b>（String），聚合侧同名字段是<b>技术键</b>（Long）—— 自动转换会在运行时
     * 解析 hex 失败（{@code NumberFormatException}）或静默写入错误值；故必须 ignore，
     * 并由 {@code GroupBuyAdminManager} 经读模型解析后显式赋值。
     */
    @Mapping(target = "spuId", ignore = true)
    @Mapping(target = "skuId", ignore = true)
    GroupBuyActivity toDomain(GroupBuyCreateReq req);

    /**
     * 拼团活动聚合 → 视图对象（AD54 创建回执 / M15 列表 / M16 详情口径）。
     *
     * <p>{@code spuId}/{@code skuId} 为业务键（ADR-061 §决策 2），技术键→业务键的换算由
     * {@code GroupBuyViewManager} 经读模型完成（同一条 SQL 亦可带出 SPU 名），故此处 ignore。
     */
    @Mapping(target = "spuId", ignore = true)
    @Mapping(target = "skuId", ignore = true)
    @Mapping(target = "teams", ignore = true)
    GroupBuyActivityVO toVO(GroupBuyActivity activity);

    /**
     * 拼团编辑请求 → 就地合并到已取聚合（V5.14 AD55）。
     *
     * <p>MapStruct 更新语义默认会把源侧 null 覆盖到目标，这里显式
     * {@code NullValuePropertyMappingStrategy.IGNORE} 保证「null-safe 局部更新」契约口径。
     *
     * @param req      编辑请求（字段全可选）
     * @param activity 已取出的活动聚合（被合并目标）
     *
     * <p>{@code spuId}/{@code skuId} 同名异义（请求 = 业务键，聚合 = 技术键）→ 显式 ignore，
     * 由 {@code GroupBuyAdminManager} 解析后显式赋值（ADR-058 §4）。
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "spuId", ignore = true)
    @Mapping(target = "skuId", ignore = true)
    void mergeGroupBuyUpdate(GroupBuyUpdateReq req, @MappingTarget GroupBuyActivity activity);

    /** 拼团团聚合 → 团视图对象（M16 团列表 / M17 团进度；不出任何 userId）。 */
    GroupTeamVO toVO(GroupTeam team);

    /** 拼团团聚合列表 → 团视图对象列表（M16 详情页最近进行中团，≤10）。 */
    List<GroupTeamVO> toGroupTeamVOList(List<GroupTeam> teams);

    /** 拼团团聚合 → 团状态契约对象（I53 join 回执，ia-api {@code GroupTeamCO}）。 */
    GroupTeamCO toCO(GroupTeam team);

    /**
     * 促销聚合 → 活动中心视图对象（M12/M13，V5.11 契约定稿）。
     *
     * <p>{@code type} 需由数字编码映射为对外契约字符串（1满减→FULL_REDUCE / 2满折→LIMIT_DISCOUNT），
     * MapStruct 无法自动完成，故用 default 方法显式装配（同 {@link #toPointsBalanceVO} 先例）；
     * 装配逻辑统一收在适配层转换器中，控制器不手写转换。
     *
     * @param promotion 促销活动聚合
     * @return 活动视图对象（type 为契约字符串；类型未知时为 null，由查询用例保证不会出现）
     */
    default ActivityVO toActivityVO(Promotion promotion) {
        if (promotion == null) {
            return null;
        }
        ActivityVO vo = new ActivityVO();
        vo.setId(promotion.getId());
        PromotionTypeEnum typeEnum = PromotionTypeEnum.of(promotion.getType());
        vo.setType(typeEnum != null ? typeEnum.getContractName() : null);
        vo.setName(promotion.getName());
        vo.setSellerId(promotion.getSellerId());
        vo.setThreshold(promotion.getThreshold());
        vo.setDiscountValue(promotion.getDiscountValue());
        vo.setMaxDiscount(promotion.getMaxDiscount());
        vo.setStartTime(promotion.getStartTime());
        vo.setEndTime(promotion.getEndTime());
        return vo;
    }

    /** 促销聚合列表 → 活动中心视图对象列表（M12；元素装配复用 {@link #toActivityVO}）。 */
    List<ActivityVO> toActivityVOList(List<Promotion> promotions);

    /**
     * 秒杀购买结果 → 视图对象。
     *
     * <p>源字段 {@code spuBusinessId}（业务键）与目标 {@code spuId} 名不同，需显式映射；
     * 源侧 {@code skuId}（<b>技术键</b>，本域内部解析用）与目标无同名字段，故不会误入线格式。
     */
    @Mapping(target = "spuId", source = "spuBusinessId")
    FlashBuyVO toFlashBuyVO(FlashBuyResult result);

    /** 签到结果 DTO → 视图对象。 */
    SignResultVO toSignResultVO(SignResultDTO dto);

    /** 积分账本聚合 → 视图对象。 */
    PointsLogVO toPointsLogVO(PointsLog pointsLog);

    /**
     * 积分余额（userId + 余额两个标量）→ 视图对象。
     *
     * <p>两个入参没有公共源对象，故用 default 方法显式装配（MapStruct 不为其生成实现）；
     * 装配逻辑仍统一收在适配层转换器中，控制器不手写转换。
     *
     * @param userId  当前登录用户ID
     * @param balance 积分总余额
     * @return 积分余额视图对象
     */
    default PointsBalanceVO toPointsBalanceVO(Long userId, long balance) {
        PointsBalanceVO vo = new PointsBalanceVO();
        vo.setUserId(userId);
        vo.setBalance(balance);
        return vo;
    }
}
