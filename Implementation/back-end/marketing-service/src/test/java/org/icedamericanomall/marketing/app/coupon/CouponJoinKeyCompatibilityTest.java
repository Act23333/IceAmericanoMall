package org.icedamericanomall.marketing.app.coupon;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionManager;
import org.h2.Driver;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverter;
import org.icedamericanomall.marketing.adapter.assembler.MarketingConverterImpl;
import org.icedamericanomall.marketing.adapter.response.CouponVO;
import org.icedamericanomall.marketing.adapter.response.UserCouponVO;
import org.icedamericanomall.marketing.domain.enums.UserCouponStatusEnum;
import org.icedamericanomall.marketing.domain.model.coupon.Coupon;
import org.icedamericanomall.marketing.domain.model.coupon.UserCoupon;
import org.icedamericanomall.marketing.domain.repository.CouponRepository;
import org.icedamericanomall.marketing.infrastructure.persistence.coupon.CouponFaceQueryImpl;
import org.icedamericanomall.marketing.infrastructure.persistence.coupon.CouponMapper;
import org.icedamericanomall.marketing.infrastructure.persistence.coupon.CouponRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>券号 join 兼容性证明（本轮核心）</b>——断言同一个券的
 * {@code UserCouponVO.couponId}（我的券 M3/M4、领取回执 M1/M2）与
 * {@code CouponVO.couponId}（券模板 M5）<b>字符串逐位相等</b>，且形态为 <b>32 位小写 hex</b>。
 *
 * <p><b>为什么必须有这个测试</b>：两个 VO 的线上字段同名（{@code coupon_id}）但在实现层曾是
 * <b>不同类型不同语义</b> —— {@code CouponVO.couponId} 是券模板业务券号（String），
 * {@code UserCouponVO.couponId} 曾是 {@code user_coupon.coupon_id}（技术外键 Long）。
 * 前端按 {@code coupon_id} 建 Map 本地 join（{@code USE /api/coupon/available} ×
 * {@code GET /api/coupon/template}）时，数字键永远匹配不上字符串键 —— <b>join 静默全空</b>，
 * 且单侧断言（各自只测「字段非空」）完全发现不了。故本测试以**同一个物理行**为源，
 * 同时走两条链路取出券号并比对：
 * <ol>
 *   <li>券模板侧（M5）：{@code coupon} 表 → {@code CouponRepositoryImpl.findByCouponId}
 *       （{@code BINARY(16)} → hex 的仓储边界转换）→ {@code MarketingConverter.toVO} → {@code CouponVO}；</li>
 *   <li>持券侧（M3/M4）：{@code coupon} 表 → {@code CouponFaceQueryImpl}（读模型顺带解出券号）
 *       → {@code UserCouponViewAssembler} → {@code UserCouponVO}。</li>
 * </ol>
 *
 * <p>真实 SQL 语义（真实 H2 表 + 真实 {@code BINARY(16)} 列），非 mock —— mock 夹具曾把两侧
 * 券号各写成自洽的值，正是这个缺陷此前逃过测试的原因。
 */
@DisplayName("coupon_id join 兼容性 — UserCouponVO.couponId == CouponVO.couponId（契约 §4.10.1）")
class CouponJoinKeyCompatibilityTest {

    private static final Long USER_ID = 9L;
    private static final Long USER_COUPON_ID = 88L;

    /** 券模板技术主键（{@code coupon.id}）—— 只允许出现在 PO/领域层。 */
    private static final Long TEMPLATE_ID = 5L;

    /** 券模板业务券号（{@code coupon.coupon_id} 的 32-hex 对外形态）。 */
    private static final String COUPON_NO = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";

    /** 业务券号的形态契约：32 位小写 hex（ADR-050）。 */
    private static final Pattern HEX32 = Pattern.compile("[0-9a-f]{32}");

    private final MarketingConverter converter = new MarketingConverterImpl();

    private CouponRepository couponRepository;
    private UserCouponViewAssembler assembler;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:coupon_join_" + hashCode()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE coupon ("
                    + "id BIGINT PRIMARY KEY, coupon_id BINARY(16), "
                    + "name VARCHAR(100), `value` INT, min_amount INT, discount_type INT)");
            stmt.execute("INSERT INTO coupon (id, coupon_id, name, `value`, min_amount, discount_type) VALUES "
                    + "(" + TEMPLATE_ID + ", X'" + COUPON_NO + "', '618满200减30', 3000, 20000, 1)");
        }

        // 券模板侧链路：真实仓储（BINARY(16) ↔ hex 转换）
        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(CouponMapper.class);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(config);
        factoryBean.setGlobalConfig(globalConfig);
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        SqlSessionManager sqlSessionManager = SqlSessionManager.newInstance(sqlSessionFactory);
        couponRepository = new CouponRepositoryImpl(sqlSessionManager.getMapper(CouponMapper.class));

        // 持券侧链路：真实读模型（同一物理行，按模板技术主键批量读）+ 同一装配器
        assembler = new UserCouponViewAssembler(converter,
                new CouponFaceQueryImpl(new JdbcTemplate(dataSource)));
    }

    @Test
    @DisplayName("我的券列表（M3）— coupon_id 与 CouponVO.coupon_id 相等，且 discount_type 与券模板同值")
    void shouldShareSameCouponId_whenListingAvailableCoupons() {
        CouponVO templateVO = couponTemplateVO();
        UserCouponVO userCouponVO = assembler.assembleList(List.of(userCoupon())).get(0);

        assertEquals(templateVO.getCouponId(), userCouponVO.getCouponId(),
                "两侧 coupon_id 必须逐位相等，否则前端按 coupon_id 本地 join 永远匹配不上");
        assertEquals(COUPON_NO, userCouponVO.getCouponId());
        assertEquals(templateVO.getDiscountType(), userCouponVO.getDiscountType(),
                "两侧 discount_type 也必须同值（同一物理行）：前端 join 后按类型渲染才一致");
        assertEquals(1, userCouponVO.getDiscountType(), "本行 discount_type=1（FIXED）");
    }

    @Test
    @DisplayName("领取回执（M1/M2）— 券号取自已加载的券模板聚合，与 CouponVO.couponId 字符串相等")
    void shouldShareSameCouponId_whenClaimReceipt() {
        CouponVO templateVO = couponTemplateVO();
        Coupon template = couponRepository.findByCouponId(COUPON_NO);
        assertNotNull(template, "同一物理行应能按业务券号（byte[] 参数）检索到模板");

        UserCouponVO receiptVO = assembler.assemble(userCoupon(), template);

        assertEquals(templateVO.getCouponId(), receiptVO.getCouponId(),
                "领取回执与券模板必须共用同一券号口径");
        assertEquals(COUPON_NO, receiptVO.getCouponId());
    }

    @Test
    @DisplayName("两侧券号形态均为 32 位小写 hex（非法/越界形态不得出现）")
    void shouldBe32Hex_onBothSides() {
        CouponVO templateVO = couponTemplateVO();
        UserCouponVO userCouponVO = assembler.assembleList(List.of(userCoupon())).get(0);

        assertTrue(HEX32.matcher(templateVO.getCouponId()).matches(),
                "CouponVO.coupon_id 形态非法: " + templateVO.getCouponId());
        assertTrue(HEX32.matcher(userCouponVO.getCouponId()).matches(),
                "UserCouponVO.coupon_id 形态非法: " + userCouponVO.getCouponId());
        assertEquals(32, userCouponVO.getCouponId().length());
    }

    @Test
    @DisplayName("join 模拟 — 按 coupon_id 建 Map 可命中；技术主键作键命不中（回归护栏）")
    void shouldBeJoinableByCouponId_andNotByTechnicalKey() {
        CouponVO templateVO = couponTemplateVO();
        UserCouponVO userCouponVO = assembler.assembleList(List.of(userCoupon())).get(0);

        // 前端 useCoupons 的实现口径：Map<CouponVO.coupon_id, CouponVO>.get(UserCouponVO.coupon_id)
        Map<String, CouponVO> templatesByCouponId = Map.of(templateVO.getCouponId(), templateVO);

        assertSame(templateVO, templatesByCouponId.get(userCouponVO.getCouponId()),
                "按 coupon_id join 必须命中同一券模板（修复前为 Long 键，get 恒为 null → 券名/券面值降级）");
        assertNull(templatesByCouponId.get(String.valueOf(TEMPLATE_ID)),
                "技术主键不是 join 键：命中它说明 VO 泄漏了 coupon.id");
        assertNotEquals(String.valueOf(TEMPLATE_ID), userCouponVO.getCouponId(),
                "coupon.id（技术主键）不得出现在任何 VO");
    }

    // ==================== 夹具 ====================

    /** 券模板侧出参（M5 {@code GET /api/coupon/template} 口径）：从同一物理行经仓储 + 装配器产出。 */
    private CouponVO couponTemplateVO() {
        Coupon template = couponRepository.findByCouponId(COUPON_NO);
        assertNotNull(template, "券模板缺失，测试夹具失效");
        CouponVO vo = converter.toVO(template);
        assertNotNull(vo, "CouponVO 装配失败");
        return vo;
    }

    /** 一张持券：只带技术外键（{@code user_coupon.coupon_id → coupon.id}），业务券号不在其上。 */
    private UserCoupon userCoupon() {
        return UserCoupon.builder()
                .id(USER_COUPON_ID)
                .userId(USER_ID)
                .couponId(TEMPLATE_ID)
                .status(UserCouponStatusEnum.UNUSED.getCode())
                .build();
    }
}
