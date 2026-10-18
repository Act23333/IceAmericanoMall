package org.icedamericanomall.fallback;

import feign.Response;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.exception.UpstreamErrorDecoder;
import org.icedamericanomall.exception.UpstreamException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 降级工厂（{@code org.icedamericanomall.fallback}）的<b>语义契约测试</b> —— 逐个工厂、逐个方法
 * 机械验证 {@code collaboration/09-constraints.md} §三·八 的两条铁律：
 *
 * <ol>
 *   <li><b>4xx 必须透传</b>：下游 {@code 4xx + 已知 code}（{@link UpstreamException}，
 *       {@code category == CLIENT}）不得被改写成本域 {@code BizException}；</li>
 *   <li><b>只有不可用时才降级</b>：{@code 5xx / 超时 / 熔断}（{@code TRANSIENT}）时，
 *       该抛错的工厂<b>必须</b>抛 {@code BizException}、该返回降级值的工厂<b>必须</b>不抛。</li>
 * </ol>
 *
 * <h2>为什么这些断言必须写在这里（而不是靠人读代码）</h2>
 * 这两条语义是 M1 / M2 两个变异体能否被「咬住」的前提：
 * <ul>
 *   <li><b>M1</b>：把「4xx 透传」改回「无条件 {@code SYSTEM_BUSY}」——
 *       由 {@link #shouldPassThrough4xx_whenDownstreamRejectsAsClientError()} 杀掉；</li>
 *   <li><b>M2</b>：把 fail-closed 的工厂改成 fail-open（抛错 → 静默返回降级值）——
 *       由 {@link #shouldFollowDispositionTable_whenDownstreamUnavailable()} 杀掉。</li>
 * </ul>
 * 因此本类<b>不</b>允许任何一处「顺手放宽」的断言：失败信息里必须能直接读出「哪个工厂哪个方法」。
 *
 * <h2>工厂发现方式</h2>
 * 扫描 {@code target/classes/org/icedamericanomall/fallback/*.class} 反射实例化，<b>不维护
 * 30 个类名的硬编码清单</b>（清单必然漂移：新增工厂时测试静默漏测）。发现结果数低于
 * {@link #MIN_EXPECTED_FACTORIES} 直接判失败 —— 防止「扫描路径失效 → 零个工厂 → 全绿」。
 *
 * <h2>处置表为什么是人工逐类判定、且只有两值</h2>
 * 若用「有没有 import {@code BizException}」自动推导处置，则 M2 变异（把
 * {@code throw new BizException(...)} 换成 {@code return ...}）在表里也会同步变成
 * {@code RETURNS}，测试依然全绿 —— <b>自动推导等于没有门禁</b>。故处置表只能人工判定：
 * 改了实现就必须来这个文件改表，评审时这处改动本身就是审查点。
 *
 * <p><b>一处必要的模型扩展</b>：本包存在 3 个「逐方法定类」的工厂（{@code AuthContextClientFallback}
 * / {@code ItemSearchClientFallback} / {@code UserClientFallback} —— ADR-011 三分法按方法定类，
 * 同一工厂内既有必须抛错的方法、也有必须返回降级值的方法），单一值无法表达。因此处置表分层：
 * {@link #FACTORY_DISPOSITIONS} 是<b>工厂级默认</b>（新增工厂必须在此登记，否则红），
 * {@link #METHOD_DISPOSITIONS} 是<b>同一工厂内的显式逐方法覆盖</b>（键 {@code 类名#方法名}，
 * 只有 3 条；陈旧条目同样判红）。两表都只有 {@link Disposition} 两个值。
 *
 * <p>调用参数的构造：{@code Mockito.mock(paramType)} + 标量默认值 —— 部分实现会直接
 * {@code request.getChannel()} / {@code request.getScene()}（不判空），所以<b>必须传 mock 而不是 null</b>。
 *
 * <h2>本类如何自证「断言真的会咬人」（自身变异）</h2>
 * 逐工厂跑真实实现全绿，只能证明「当前实现合规」，证明不了「检测逻辑会红」—— 一个恒真的判定
 * （比如把扫描结果丢掉）同样全绿。故两段扫描循环抽成
 * {@link #scanClientErrorPassThrough(Class, Object, int)} /
 * {@link #scanUnavailableViolations(Class, Object, ExpectationLookup)}（真实扫描与前文<b>共用同一份</b>判定），
 * 再用合成违规客户端反向喂它：
 * <ul>
 *   <li>{@link RewriteToBusyFactory}：CLIENT（4xx）场景下无条件抛 {@code BizException(SYSTEM_BUSY)}
 *       —— M1 变异形态，必须被 4xx 扫描判红；</li>
 *   <li>{@link SilentFailOpenFactory}：不可用时应抛却不抛（静默返回降级值）—— M2 变异形态，
 *       必须被不可用扫描判红；</li>
 *   <li>两者在对方场景下必须<b>零违规</b>（反向对照）—— 否则「恒红」的检测逻辑也能让上面两条通过。</li>
 * </ul>
 */
@DisplayName("降级工厂语义契约 — 4xx 透传 / 不可用时按处置表分流（逐工厂、逐方法）")
class FallbackDegradeContractTest {

    /** 降级工厂所在包（相对 surefire 工作目录 = 模块根）。 */
    private static final String FALLBACK_PACKAGE = "org.icedamericanomall.fallback";
    private static final String FALLBACK_PACKAGE_PATH = "org/icedamericanomall/fallback";

    /** 发现数量下限：低于此值说明扫描失效（而不是「工厂变少了」），必须判红。 */
    private static final int MIN_EXPECTED_FACTORIES = 30;

    private static final String TYPED_404 = "{\"code\":1103,\"msg\":\"商品不存在或已删除\","
            + "\"category\":\"CLIENT\",\"retryable\":false,\"traceId\":\"trace-down-1\"}";

    private static final String TYPED_503 = "{\"code\":1001,\"msg\":\"系统繁忙\","
            + "\"category\":\"TRANSIENT\",\"retryable\":true,\"traceId\":\"trace-down-2\"}";

    /** 4xx 透传断言里被透传的下游业务码（与 {@link #TYPED_404} 一致）。 */
    private static final int DOWNSTREAM_CLIENT_CODE = 1103;

    // ==================================================================================
    // 处置表（人工逐类判定 —— 读实现源码得出，禁止由「有没有 import BizException」推导）
    // ==================================================================================

    /** 降级工厂在「不可用（TRANSIENT/BUG）」时的既定处置。 */
    enum Disposition {
        /** 抛 {@code BizException}（fail-closed）；除 {@link #NON_SYSTEM_BUSY_CODES} 登记项外必须是 {@code SYSTEM_BUSY}。 */
        THROWS,
        /** 返回降级值，不抛异常（fail-open / fail-soft / 结构化失败）。 */
        RETURNS
    }

    /**
     * <b>工厂级默认处置</b>（键 = 工厂简单类名）。新增工厂若不在此登记，
     * {@link #shouldRegisterEveryDiscoveredFactoryInDispositionTable()} 直接判红。
     */
    private static final Map<String, Disposition> FACTORY_DISPOSITIONS = buildFactoryDispositions();

    /**
     * <b>同一工厂内的逐方法覆盖</b>（键 = {@code 工厂简单类名#方法名}）。
     *
     * <p>只用于 ADR-011「逐方法定类」的工厂：同一工厂里既有必须抛错的方法，也有必须返回降级值的方法。
     * 条目陈旧（类或方法已不存在）同样判红。
     */
    private static final Map<String, Disposition> METHOD_DISPOSITIONS = Map.of(
            // 授权变更静默丢失 → 角色与 RBAC 五表不一致，必须 fail-closed；context / rolesByUserIds / revokeUserTokens 则 fail-open。
            "AuthContextClientFallback#setUserRole", Disposition.THROWS,
            // reindex 全量数据源：空页会让索引被重建为 0 条文档，必须 fail-closed；searchPage / suggest / detail 是①展示型。
            "ItemSearchClientFallback#exportPage", Disposition.THROWS,
            // U17（2026-10-18）：清边的「静默空操作」让调用方以为清边成功，残留边会一直挂在图谱里。
            // 工厂级仍 RETURNS（edgeUpsert 走哨兵 -1、neighbors 走空列表，二者按 PRD §16.8.1 全链路 fail-open），
            // 只有这一个方法改抛 —— 调用方 KgEdgeIngestManager#deleteEdgesSafely 自带 try/catch。
            "KgClientFallback#deleteEdges", Disposition.THROWS,
            // 用户建档：返回 null 会触发 user_auth.user_id 非空约束违约，必须 fail-closed；countUsers 是①展示型。
            "UserClientFallback#getOrCreateUser", Disposition.THROWS);

    /**
     * <b>{@code THROWS} 里不抛 {@code SYSTEM_BUSY} 的既定例外</b>（键 = {@code 工厂简单类名#方法名}）。
     *
     * <p>通知通道保留自身通道级错误码（调用方据此决定「换通道 / 重试」而不是笼统「系统繁忙」），
     * 见 {@code NotificationClientFallback} 类注释。每加一条都等于放宽一处断言，评审必须说明理由。
     */
    private static final Map<String, ErrorCode> NON_SYSTEM_BUSY_CODES = Map.of(
            "NotificationClientFallback#sendSms", ErrorCode.SMS_SEND_FAILED,
            "NotificationClientFallback#sendEmail", ErrorCode.EMAIL_SEND_FAILED,
            "NotificationClientFallback#sendInAppMessage", ErrorCode.BUSINESS_EXECUTION_EXCEPTION);

    private static Map<String, Disposition> buildFactoryDispositions() {
        Map<String, Disposition> table = new LinkedHashMap<>();
        // ---- fail-closed：不可用时抛 BizException ----
        table.put("AfterSaleClientFallback", Disposition.THROWS);
        table.put("AddressClientFallback", Disposition.THROWS);
        table.put("BalanceClientFallback", Disposition.THROWS);
        table.put("CartClientFallback", Disposition.THROWS);
        table.put("CouponClientFallback", Disposition.THROWS);
        table.put("CurrencyClientFallback", Disposition.THROWS);
        table.put("FreightTemplateClientFallback", Disposition.THROWS);
        table.put("GroupBuyClientFallback", Disposition.THROWS);
        table.put("GroupBuyTeamClientFallback", Disposition.THROWS);
        table.put("LogisticsClientFallback", Disposition.THROWS);
        table.put("NotificationClientFallback", Disposition.THROWS);
        table.put("OrderClientFallback", Disposition.THROWS);
        table.put("OrderRefundClientFallback", Disposition.THROWS);
        table.put("PointsClientFallback", Disposition.THROWS);
        table.put("PromotionClientFallback", Disposition.THROWS);
        table.put("RefundClientFallback", Disposition.THROWS);
        table.put("SkuClientFallback", Disposition.THROWS);
        table.put("SpuDetailClientFallback", Disposition.THROWS);
        table.put("TradeInvoiceClientFallback", Disposition.THROWS);
        // ---- fail-open / fail-soft：不可用时返回降级值（不抛）----
        // 注意：其中 AuthContextClientFallback / ItemSearchClientFallback / UserClientFallback 的个别方法
        // 由 METHOD_DISPOSITIONS 覆盖为 THROWS —— 这里登记的是「工厂级默认」。
        table.put("AuthContextClientFallback", Disposition.RETURNS);
        table.put("ItemSearchClientFallback", Disposition.RETURNS);
        table.put("KnowledgeVectorClientFallback", Disposition.RETURNS);
        table.put("MemberClientFallback", Disposition.RETURNS);
        table.put("KgClientFallback", Disposition.RETURNS);
        table.put("NoteVectorClientFallback", Disposition.RETURNS);
        table.put("SearchClientFallback", Disposition.RETURNS);
        table.put("SpuVectorClientFallback", Disposition.RETURNS);
        table.put("TaskClientFallback", Disposition.RETURNS);
        table.put("UserClientFallback", Disposition.RETURNS);
        table.put("UserProfileClientFallback", Disposition.RETURNS);
        table.put("VerificationClientFallback", Disposition.RETURNS);
        return Map.copyOf(table);
    }

    // ==================================================================================
    // ① 发现 + 处置表完整性（防「扫描失效静默通过」与「新工厂未登记」）
    // ==================================================================================

    @Test
    @DisplayName("发现全部降级工厂（≥30），并输出处置表计数")
    void shouldDiscoverEveryFallbackFactory() {
        List<Class<?>> discovered = discoverFactoryClasses();

        System.out.println("[fallback-contract] 发现降级工厂 = " + discovered.size() + " 个");
        System.out.println("[fallback-contract] 处置表工厂级 THROWS = " + countDispositions(FACTORY_DISPOSITIONS, Disposition.THROWS));
        System.out.println("[fallback-contract] 处置表工厂级 RETURNS = " + countDispositions(FACTORY_DISPOSITIONS, Disposition.RETURNS));
        System.out.println("[fallback-contract] 逐方法覆盖（同一工厂内混合处置） = " + METHOD_DISPOSITIONS.size()
                + " 条 -> " + new TreeSet<>(METHOD_DISPOSITIONS.keySet()));
        long throwsMethods = 0;
        long returnsMethods = 0;
        for (Class<?> factoryClass : discovered) {
            Object client = instantiate(factoryClass).create(unavailableCause());
            for (Method method : clientMethods(client)) {
                if (dispositionOf(factoryClass, method) == Disposition.THROWS) {
                    throwsMethods++;
                } else {
                    returnsMethods++;
                }
            }
        }
        System.out.println("[fallback-contract] 逐方法合计: THROWS = " + throwsMethods + " / RETURNS = " + returnsMethods
                + " / 合计 = " + (throwsMethods + returnsMethods));

        assertThat(discovered)
                .as("降级工厂发现数低于 %d —— 扫描失效了吗？（expected: 读 %s/*.class；surefire 工作目录应为模块根）",
                        MIN_EXPECTED_FACTORIES, FALLBACK_PACKAGE_PATH)
                .hasSizeGreaterThanOrEqualTo(MIN_EXPECTED_FACTORIES);
        assertThat(discovered).as("发现结果里混入了非 FallbackFactory 类型").allMatch(c -> FallbackFactory.class.isAssignableFrom(c));
    }

    @Test
    @DisplayName("处置表与实现保持一致：新工厂未分类 / 陈旧条目 一律判红（双向）")
    void shouldRegisterEveryDiscoveredFactoryInDispositionTable() {
        List<Class<?>> discovered = discoverFactoryClasses();
        Set<String> discoveredNames = discovered.stream()
                .map(Class::getSimpleName)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> registeredNames = new TreeSet<>(FACTORY_DISPOSITIONS.keySet());

        Set<String> unclassified = new TreeSet<>(discoveredNames);
        unclassified.removeAll(registeredNames);
        assertThat(unclassified)
                .as("新工厂未分类，请在处置表中登记（FACTORY_DISPOSITIONS / METHOD_DISPOSITIONS）：%s", unclassified)
                .isEmpty();

        Set<String> stale = new TreeSet<>(registeredNames);
        stale.removeAll(discoveredNames);
        assertThat(stale)
                .as("处置表存在陈旧条目（类已删除或改名），请清理：%s", stale)
                .isEmpty();

        // 逐方法覆盖 / 非 SYSTEM_BUSY 例外：引用的类与方法都必须真实存在
        Map<String, List<String>> methodsByFactory = new LinkedHashMap<>();
        for (Class<?> factoryClass : discovered) {
            Object client = instantiate(factoryClass).create(unavailableCause());
            List<Method> methods = clientMethods(client);
            List<String> names = methods.stream().map(Method::getName).collect(Collectors.toList());
            methodsByFactory.put(factoryClass.getSimpleName(), names);
            assertThat(new LinkedHashSet<>(names))
                    .as("工厂 %s 的接口方法出现同名重载 —— 处置表以「类名#方法名」为键，重载会被静默判错；"
                            + "请把键改为含参数列表", factoryClass.getSimpleName())
                    .hasSameSizeAs(names);
        }

        Set<String> staleMethodEntries = new TreeSet<>();
        Set<String> methodKeys = new TreeSet<>(METHOD_DISPOSITIONS.keySet());
        methodKeys.addAll(NON_SYSTEM_BUSY_CODES.keySet());
        for (String key : methodKeys) {
            int separator = key.indexOf('#');
            assertThat(separator).as("处置表键格式必须是「类名#方法名」: %s", key).isGreaterThan(0);
            String factory = key.substring(0, separator);
            String method = key.substring(separator + 1);
            List<String> methods = methodsByFactory.get(factory);
            if (methods == null || !methods.contains(method)) {
                staleMethodEntries.add(key);
            }
        }
        assertThat(staleMethodEntries)
                .as("逐方法处置表 / 非 SYSTEM_BUSY 例外表存在陈旧条目（类或方法已不存在），请清理：%s", staleMethodEntries)
                .isEmpty();

        // 非 SYSTEM_BUSY 例外只能挂在 THROWS 方法上（RETURNS 方法不该有错误码）
        for (String key : NON_SYSTEM_BUSY_CODES.keySet()) {
            String factory = key.substring(0, key.indexOf('#'));
            String method = key.substring(key.indexOf('#') + 1);
            Disposition disposition = METHOD_DISPOSITIONS.getOrDefault(key, FACTORY_DISPOSITIONS.get(factory));
            assertThat(disposition)
                    .as("键 %s 登记了错误码，但它在处置表里不是 THROWS", key)
                    .isEqualTo(Disposition.THROWS);
            assertThat(methodsByFactory.get(factory)).as("类 %s 不存在方法 %s", factory, method).contains(method);
        }
    }

    /**
     * <b>{@code void} 方法在 4xx 分支「静默返回」的登记表</b>（键 = {@code 工厂简单类名#方法名}）。
     * <b>当前为空</b> —— 全部 {@code void} 降级方法在 4xx 下都已抛（F 批改造的结果）。
     *
     * <h2>这条规则补的是什么洞（M8 实测发现的，不是推演）</h2>
     * 处置表（{@link #FACTORY_DISPOSITIONS} / {@link #METHOD_DISPOSITIONS}）里的 {@code THROWS}
     * <b>只约束「不可用」那一支</b>。而 CLIENT（4xx）分支的判据是「有没有抛异常」——
     * 对<b>有返回值</b>的方法，静默返回至少还能被调用方看见（拿到降级值）；
     * 对 <b>{@code void}</b> 方法，<b>静默返回与「真的执行成功」在调用方眼里完全同形</b>。
     * 于是「把 {@code throw} 改回 {@code return}」这种回归<b>不会被后者咬住</b>：
     * M8 实测（把 {@code AfterSaleClientFallback#markRefunded} 的 {@code throw upstream} 换成
     * {@code return}，处置表仍为 {@code THROWS}）→ 全部用例照旧绿。
     *
     * <p>故补一条：<b>{@code void} 方法在 4xx 下不许静默返回</b>，除非登记在本表并写清理由。
     * 登记项<b>陈旧即失败</b> —— 见 {@link #shouldKeepVoidSilentWhitelistExact()}：
     * 它把「扫描到的 void + 静默」集合与登记表<b>双向比对</b>，多一条少一条都红。
     *
     * <p><b>⚠ 别把「不可用分支的静默」塞进这张表</b>：{@code AuthContextClientFallback#revokeUserTokens}
     * 在 4xx 下是抛的（所以不在此表），它只在<b>不可用</b>分支静默 —— 那由处置表的
     * {@code RETURNS} 管，理由写在 {@code open-items} 的 U17 行（调用点无 try/catch，需先补防护）。
     * 两张表的职责不同：这张管「4xx 底下有没有真的抛」，处置表管「不可用时应抛还是应返回」。
     *
     * <h2>检测边界（空集只有在这些前提下才可信 —— 否则是假阴性）</h2>
     * <ul>
     *   <li><b>只看 4xx（CLIENT）分支</b>：不可用分支由处置表（{@code THROWS}/{@code RETURNS}）管。</li>
     *   <li><b>只看 {@code void} 方法</b>：有返回值的方法即便静默返回，调用方至少拿到一个降级值、
     *       能自己判断；{@code void} 的静默与成功同形，才是本条要堵的形态。</li>
     *   <li><b>只看「没抛」</b>：抛了但抛错类型（不是 {@code UpstreamException}）由 ② 的 CLIENT 扫描判违规，
     *       两处分工不重叠。</li>
     *   <li><b>扫描面 = {@code ia-api/target/classes} 里 {@code org.icedamericanomall.fallback} 下的
     *       {@code FallbackFactory} 实现</b>（编译产物，非源码扫描）。<b>其它包/模块的 {@code void}
     *       降级方法不在覆盖范围内</b> —— 新增降级工厂请放这个包，否则本条看不见它。</li>
     *   <li><b>空集本身不是证据</b>：必须同时证明「确实扫到了若干个 {@code void} 方法」，
     *       见 {@link #MIN_SCANNED_VOID_METHODS} 的下限断言。</li>
     * </ul>
     */
    private static final Map<String, String> VOID_CLIENT_SILENT_ALLOWED = Map.of();

    /**
     * 扫描面下限（<b>防假阴性</b>）：谓词或输出目录一旦失效，扫到的 {@code void} 方法会掉到 0，
     * 那时「静默集合为空」与登记表（也为空）恰好相等 —— <b>空集会被当成合规</b>，规则静默失效。
     *
     * <p>取下限而非等值：新增 {@code void} 降级方法时不必改这里（只增不减），
     * 但掉到 0 或明显变少会立刻红。下限值取自改造完成时的实测数（留余量，不追求精确）。
     */
    private static final int MIN_SCANNED_VOID_METHODS = 10;

    /** 处置表按「类名#方法名」寻址（与 {@link #METHOD_DISPOSITIONS} 同一口径）。 */

    /**
     * ★ <b>登记表必须与实现严格一致（双向）</b>：扫描到的「{@code void} + 4xx 静默返回」集合，
     * 必须与 {@link #VOID_CLIENT_SILENT_ALLOWED} 的键集合逐一对应。
     *
     * <p>双向而非单向的理由：只查「新增未登记」会让删掉实现后的登记项永远留着（白名单膨胀成免责名单）；
     * 只查「陈旧」则挡不住新出现的静默方法。
     */
    @Test
    @DisplayName("★ void 方法 4xx 静默返回的登记表 — 必须与实现双向一致（新增未登记 / 陈旧登记 都红）")
    void shouldKeepVoidSilentWhitelistExact() {
        Set<String> silentVoidMethods = new TreeSet<>();
        int scannedVoidMethods = 0;
        for (Class<?> factoryClass : discoverFactoryClasses()) {
            Object client = instantiate(factoryClass).create(clientErrorCause());
            for (Method method : clientMethods(client)) {
                if (method.getReturnType() != void.class) {
                    continue;
                }
                scannedVoidMethods++;
                if (invokeCapturingFailure(client, method) == null) {
                    silentVoidMethods.add(methodKey(factoryClass, method));
                }
            }
        }

        // 反空断言必须落在「扫描面」上，而不是「违规集合」上 —— 后者的正确状态本来就可能是空
        // （当前全部 void 方法在 4xx 下都抛）。写成「违规集合非空」会在代码正确时反而变红；
        // 写成「isPositive」又太弱（谓词退化到只剩 1 个方法也会通过）。故用下限 + 打印实测数。
        System.out.println("[fallback-contract] 4xx 扫描面: 扫到 void 方法 = " + scannedVoidMethods
                + " 个 / 其中静默返回 = " + silentVoidMethods.size() + " 个 -> " + silentVoidMethods);
        assertThat(scannedVoidMethods)
                .as("扫描面过小（%d < %d）：谓词或输出目录可能失效，此时「静默集合为空」是假阴性，不是合规",
                        scannedVoidMethods, MIN_SCANNED_VOID_METHODS)
                .isGreaterThanOrEqualTo(MIN_SCANNED_VOID_METHODS);

        assertThat(silentVoidMethods)
                .as("void 方法在 4xx 下静默返回的集合与登记表不一致 —— "
                        + "新增的请登记并写清理由（改抛优先），已不再静默的请从表里删掉")
                .containsExactlyInAnyOrderElementsOf(VOID_CLIENT_SILENT_ALLOWED.keySet());
    }

    // ==================================================================================
    // ② CLIENT（4xx 透传）—— 杀掉 M1「无条件 SYSTEM_BUSY」
    // ==================================================================================

    @Test
    @DisplayName("★ 下游 4xx（category=CLIENT）→ 一律不得改写成本域 BizException；抛出则必须是 UpstreamException(1103)")
    void shouldPassThrough4xx_whenDownstreamRejectsAsClientError() {
        UpstreamException clientCause = clientErrorCause();
        List<String> violations = new ArrayList<>();
        int passedThrough = 0;
        int returnedDegradedValue = 0;

        for (Class<?> factoryClass : discoverFactoryClasses()) {
            Object client = instantiate(factoryClass).create(clientCause);
            ClientErrorScan scan = scanClientErrorPassThrough(factoryClass, client, DOWNSTREAM_CLIENT_CODE);
            violations.addAll(scan.violations());
            passedThrough += scan.passedThrough();
            returnedDegradedValue += scan.returnedDegradedValue();
        }

        System.out.println("[fallback-contract] 4xx 场景分支覆盖: 透传 UpstreamException = " + passedThrough
                + " 个方法 / 返回降级值（既定豁免透传） = " + returnedDegradedValue + " 个方法");

        assertThat(violations)
                .as("下列工厂方法在下游 4xx 时未原样透传（M1 变异「无条件 SYSTEM_BUSY」的形态）")
                .isEmpty();
    }

    // ==================================================================================
    // ③ 不可用（TRANSIENT）—— 杀掉 M2「fail-closed 改成 fail-open」
    // ==================================================================================

    @Test
    @DisplayName("★ 下游不可用（503/TRANSIENT）→ THROWS 必抛 BizException(处置错误码)，RETURNS 必不抛")
    void shouldFollowDispositionTable_whenDownstreamUnavailable() {
        UpstreamException unavailableCause = unavailableCause();
        List<String> violations = new ArrayList<>();

        for (Class<?> factoryClass : discoverFactoryClasses()) {
            Object client = instantiate(factoryClass).create(unavailableCause);
            violations.addAll(scanUnavailableViolations(factoryClass, client,
                    method -> new UnavailableExpectation(
                            dispositionOf(factoryClass, method), expectedErrorCode(factoryClass, method))));
        }

        assertThat(violations)
                .as("下列工厂方法在「下游不可用」时与处置表不符（M2 变异「fail-closed → fail-open」的形态）")
                .isEmpty();
    }

    // ==================================================================================
    // ④ 检测逻辑本体（②③ 的真实扫描与 ⑤ 的自证共用同一份判定，抽出来才能反向喂合成输入）
    // ==================================================================================

    /** 「下游 4xx」扫描结果：透传 / 返回降级值 的计数 + 违规列表。 */
    record ClientErrorScan(int passedThrough, int returnedDegradedValue, List<String> violations) {
    }

    /**
     * 「下游 4xx 必须原样透传」的检测逻辑 —— <b>唯一一份</b>判定，② 与 ⑤ 都调它。
     *
     * <p>4xx 场景返回降级值不判违规（{@code KgClientFallback#neighbors} / {@code MemberClientFallback} /
     * {@code AfterSaleClientFallback} 的同行方法等，按各自类注释显式豁免透传，属既定例外），但<b>必须</b>计入
     * {@link ClientErrorScan#returnedDegradedValue()} —— 否则「零违规」可能只是「一个方法都没扫到」。
     * <p>⚠ {@code AfterSaleClientFallback#markRefunded} 自 U17（2026-10-18）起<b>不再</b>属于这里的例外，
     * 它已改为 4xx 透传 —— 别再从这份名单里给它找豁免。
     */
    static ClientErrorScan scanClientErrorPassThrough(Class<?> factoryClass, Object client, int downstreamClientCode) {
        List<String> violations = new ArrayList<>();
        int passedThrough = 0;
        int returnedDegradedValue = 0;
        for (Method method : clientMethods(client)) {
            Throwable thrown = invokeCapturingFailure(client, method);
            if (thrown == null) {
                // void 方法的静默返回在调用方眼里与「真的执行了」完全同形 ⇒ 不许无声发生（M8 实测的洞）
                if (method.getReturnType() == void.class
                        && !VOID_CLIENT_SILENT_ALLOWED.containsKey(methodKey(factoryClass, method))) {
                    violations.add(describe(factoryClass, method)
                            + " 是 void 方法却在 4xx 下静默返回 —— 调用方无法区分「已执行」与「根本没执行」；"
                            + "请改抛 UpstreamException（4xx 透传），或登记到 VOID_CLIENT_SILENT_ALLOWED 并写清理由");
                }
                returnedDegradedValue++;
                continue;
            }
            passedThrough++;
            String where = describe(factoryClass, method);
            if (thrown instanceof BizException biz) {
                violations.add(where + " 把下游 4xx 改写成了 BizException(" + biz.getErrorCode()
                        + ")，破坏了 §三·八·1「4xx 原样透传」");
            } else if (!(thrown instanceof UpstreamException upstream)) {
                violations.add(where + " 抛出 " + thrown.getClass().getName()
                        + " —— 4xx 分支只允许抛出原始 UpstreamException");
            } else if (upstream.getCode() != downstreamClientCode) {
                violations.add(where + " 透传时改了下游业务码：期望 " + downstreamClientCode
                        + "，实际 " + upstream.getCode());
            }
        }
        return new ClientErrorScan(passedThrough, returnedDegradedValue, List.copyOf(violations));
    }

    /** 一个工厂方法在「不可用」场景的既定期望（处置 + 期望错误码；{@code RETURNS} 时期望错误码为 null）。 */
    record UnavailableExpectation(Disposition disposition, ErrorCode expectedCode) {
    }

    /** 期望来源：②③ 的真实扫描读处置表（{@link #dispositionOf}），⑤ 的自证喂合成期望。 */
    @FunctionalInterface
    interface ExpectationLookup {
        UnavailableExpectation expectationOf(Method method);
    }

    /** 「不可用时按处置表分流」的检测逻辑 —— <b>唯一一份</b>判定，③ 与 ⑤ 都调它。 */
    static List<String> scanUnavailableViolations(Class<?> factoryClass, Object client, ExpectationLookup lookup) {
        List<String> violations = new ArrayList<>();
        for (Method method : clientMethods(client)) {
            UnavailableExpectation expectation = lookup.expectationOf(method);
            String where = describe(factoryClass, method) + "（处置=" + expectation.disposition()
                    + (expectation.expectedCode() == null ? "" : ", 期望错误码=" + expectation.expectedCode()) + "）";
            Throwable thrown = invokeCapturingFailure(client, method);

            if (expectation.disposition() == Disposition.THROWS) {
                if (!(thrown instanceof BizException biz)) {
                    violations.add(where + " 未抛 BizException，实际 "
                            + (thrown == null ? "正常返回（fail-open —— 静默成功 = 操作丢了）" : "抛 " + thrown.getClass().getName()));
                } else if (biz.getErrorCode() != expectation.expectedCode()) {
                    violations.add(where + " 错误码不符：实际 " + biz.getErrorCode());
                }
            } else if (thrown != null) {
                violations.add(where + " 不应抛出，实际抛 " + thrown.getClass().getName() + ": " + thrown.getMessage());
            }
        }
        return violations;
    }

    // ==================================================================================
    // ⑤ 自身变异自证：合成违规客户端 → 检测逻辑必须报违规（反向对照必须零违规）
    //
    // 这一节回答的是「②③ 的断言凭什么可信」：逐 30+ 个真实工厂全绿，只能说明当前实现合规；
    // 一个恒真的判定（比如把扫描结果丢掉）同样全绿。故用合成变异体反向喂同一份检测逻辑。
    // ==================================================================================

    /** 合成探针接口（不依赖任何真实 Feign 契约，方法只有一个，失败信息里就是 {@code #call()}）。 */
    interface ProbeClient {
        String call();
    }

    /**
     * 合成变异体 <b>M1 形态</b>：「CLIENT（4xx）场景下无条件抛 {@code BizException(SYSTEM_BUSY)}」。
     *
     * <p>4xx 扫描必须判它违规；而不可用（TRANSIENT）场景下抛 {@code SYSTEM_BUSY} 恰好合规，
     * 所以它同时充当不可用扫描的<b>反向对照</b>（期望零违规）。
     */
    static final class RewriteToBusyFactory implements FallbackFactory<ProbeClient> {
        @Override
        public ProbeClient create(Throwable cause) {
            return () -> {
                throw new BizException(ErrorCode.SYSTEM_BUSY);
            };
        }
    }

    /**
     * 合成变异体 <b>M2 形态</b>：「应当抛却不抛」—— 静默返回降级值（fail-closed 被改成 fail-open）。
     *
     * <p>不可用扫描必须判它违规；4xx 场景返回降级值属既定豁免，故也是 4xx 扫描的反向对照。
     */
    static final class SilentFailOpenFactory implements FallbackFactory<ProbeClient> {
        @Override
        public ProbeClient create(Throwable cause) {
            return () -> null;
        }
    }

    @Test
    @DisplayName("★ 自证 — 4xx 扫描对「CLIENT 场景无条件抛 SYSTEM_BUSY」的合成客户端必须报违规；返回降级值不误判")
    void shouldFlagSyntheticClientThatRewritesClientErrorToSystemBusy() {
        Class<?> violatingFactory = RewriteToBusyFactory.class;
        Object violatingClient = new RewriteToBusyFactory().create(clientErrorCause());

        ClientErrorScan violating = scanClientErrorPassThrough(violatingFactory, violatingClient, DOWNSTREAM_CLIENT_CODE);
        System.out.println("[fallback-contract][mutation] 4xx 扫描 × 合成违规客户端 -> " + violating.violations());

        assertThat(violating.violations())
                .as("4xx 检测逻辑必须咬住「把下游 4xx 改写成 SYSTEM_BUSY」的客户端 —— 咬不住则 ② 的断言是空的")
                .hasSize(1);
        assertThat(violating.violations().get(0))
                .as("违规文案必须点名「哪个工厂哪个方法」并说明改写成了什么码")
                .contains("RewriteToBusyFactory#call()")
                .contains("SYSTEM_BUSY")
                .contains("4xx 原样透传");

        ClientErrorScan tolerated = scanClientErrorPassThrough(violatingFactory,
                new SilentFailOpenFactory().create(clientErrorCause()), DOWNSTREAM_CLIENT_CODE);
        assertThat(tolerated.violations()).as("4xx 场景返回降级值不得被判违规（既定豁免透传）").isEmpty();
        assertThat(tolerated.returnedDegradedValue()).as("必须真的扫到了那个方法（否则「零违规」毫无意义）").isEqualTo(1);
    }

    @Test
    @DisplayName("★ 自证 — 不可用扫描对「应当抛却不抛」的合成客户端必须报违规；无条件抛 SYSTEM_BUSY 不误判")
    void shouldFlagSyntheticClientThatSwallowsUnavailableFailure() {
        ExpectationLookup throwsSystemBusy =
                method -> new UnavailableExpectation(Disposition.THROWS, ErrorCode.SYSTEM_BUSY);
        Class<?> violatingFactory = SilentFailOpenFactory.class;
        Object violatingClient = new SilentFailOpenFactory().create(unavailableCause());

        List<String> violating = scanUnavailableViolations(violatingFactory, violatingClient, throwsSystemBusy);
        System.out.println("[fallback-contract][mutation] 不可用扫描 × 合成违规客户端 -> " + violating);

        assertThat(violating)
                .as("不可用检测逻辑必须咬住「应抛却不抛（fail-open）」的客户端 —— 咬不住则 ③ 的断言是空的")
                .hasSize(1);
        assertThat(violating.get(0))
                .as("违规文案必须点名工厂方法，并说明「正常返回 = 静默成功 = 操作丢了」")
                .contains("SilentFailOpenFactory#call()")
                .contains("fail-open");

        List<String> compliant = scanUnavailableViolations(RewriteToBusyFactory.class,
                new RewriteToBusyFactory().create(unavailableCause()), throwsSystemBusy);
        assertThat(compliant).as("不可用场景下抛 SYSTEM_BUSY 是合规的，不得误判").isEmpty();
    }

    // ==================================================================================
    // 基础设施：发现 / 实例化 / 调用 / 造 cause
    // ==================================================================================

    /**
     * 扫描 {@code target/classes/org/icedamericanomall/fallback/*.class} 发现全部降级工厂类。
     *
     * <p>不硬编码类名清单（清单会漂）；扫描失败直接失败而不是返回空列表。
     */
    private static List<Class<?>> discoverFactoryClasses() {
        Path directory = Paths.get("target", "classes", FALLBACK_PACKAGE_PATH);
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("缺少降级工厂编译产物: " + directory.toAbsolutePath()
                    + " —— 请先编译 ia-api（surefire 工作目录应为模块根 ia-api/）");
        }
        try (Stream<Path> files = Files.list(directory)) {
            List<String> simpleNames = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".class"))
                    .map(name -> name.substring(0, name.length() - ".class".length()))
                    .filter(name -> name.endsWith("Fallback"))
                    .sorted()
                    .collect(Collectors.toList());
            List<Class<?>> discovered = new ArrayList<>();
            for (String simpleName : simpleNames) {
                Class<?> type = Class.forName(FALLBACK_PACKAGE + "." + simpleName);
                if (FallbackFactory.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers())) {
                    discovered.add(type);
                }
            }
            return discovered;
        } catch (java.io.IOException | ClassNotFoundException e) {
            throw new IllegalStateException("扫描降级工厂失败: " + directory.toAbsolutePath(), e);
        }
    }

    /** 反射实例化工厂（它们都是无参 {@code @Component}）。 */
    private static FallbackFactory<?> instantiate(Class<?> factoryClass) {
        try {
            Constructor<?> constructor = factoryClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (FallbackFactory<?>) constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法实例化降级工厂 " + factoryClass.getName()
                    + " —— 本测试要求工厂保持无参构造（@Component 形态）", e);
        }
    }

    /** 工厂产出的降级客户端上的全部接口方法（排序稳定，便于失败信息复现）。 */
    private static List<Method> clientMethods(Object client) {
        return Arrays.stream(client.getClass().getMethods())
                .filter(method -> method.getDeclaringClass() != Object.class)
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .sorted(Comparator.comparing(Method::getName).thenComparingInt(Method::getParameterCount))
                .collect(Collectors.toList());
    }

    /** 调用并返回失败（无失败返回 {@code null}）；{@link InvocationTargetException} 解包成真实异常。 */
    private static Throwable invokeCapturingFailure(Object client, Method method) {
        Object[] arguments = argumentsFor(method);
        try {
            method.invoke(client, arguments);
            return null;
        } catch (InvocationTargetException e) {
            return e.getTargetException();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("反射调用失败: " + method, e);
        }
    }

    private static Object[] argumentsFor(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            arguments[i] = stubArgument(parameterTypes[i]);
        }
        return arguments;
    }

    /**
     * 参数造桩：标量给默认值、集合给空集合、其余对象用 {@code Mockito.mock}。
     *
     * <p><b>必须是 mock 而不是 {@code null}</b>：部分实现直接 {@code request.getChannel()} /
     * {@code request.getScene()} 而不判空，传 null 会 NPE 从而掩盖真实语义。
     */
    private static Object stubArgument(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.FALSE;
        }
        if (type == byte.class || type == Byte.class) {
            return (byte) 0;
        }
        if (type == short.class || type == Short.class) {
            return (short) 0;
        }
        if (type == char.class || type == Character.class) {
            return '\0';
        }
        if (type == int.class || type == Integer.class) {
            return 0;
        }
        if (type == long.class || type == Long.class) {
            return 0L;
        }
        if (type == float.class || type == Float.class) {
            return 0F;
        }
        if (type == double.class || type == Double.class) {
            return 0D;
        }
        if (type == String.class || type == CharSequence.class) {
            return "";
        }
        if (type == List.class || type == Collection.class || type == Iterable.class) {
            return List.of();
        }
        if (type == Set.class) {
            return Set.of();
        }
        if (type == Map.class) {
            return Map.of();
        }
        if (type == Optional.class) {
            return Optional.empty();
        }
        return Mockito.mock(type);
    }

    /** 模拟 Feign 的真实产物：非 2xx → {@link UpstreamErrorDecoder} → {@link UpstreamException}。 */
    private static UpstreamException decodeFailure(int status, String body) {
        Response.Builder builder = Response.builder()
                .status(status)
                .reason("r" + status)
                .headers(Map.of())
                .request(feign.Request.create(feign.Request.HttpMethod.GET,
                        "http://downstream/internal/probe", Map.of(), (byte[]) null,
                        StandardCharsets.UTF_8, null));
        Response response = builder.body(body, StandardCharsets.UTF_8).build();
        return (UpstreamException) new UpstreamErrorDecoder().decode("FallbackContractProbe#call()", response);
    }

    private static UpstreamException clientErrorCause() {
        UpstreamException cause = decodeFailure(404, TYPED_404);
        assertThat(cause.getCategory().name()).as("404 必须解码为 CLIENT 类别（否则本测试的前置假设不成立）")
                .isEqualTo("CLIENT");
        return cause;
    }

    private static UpstreamException unavailableCause() {
        UpstreamException cause = decodeFailure(503, TYPED_503);
        assertThat(cause.getCategory().name()).as("503 必须解码为 TRANSIENT 类别（否则本测试的前置假设不成立）")
                .isEqualTo("TRANSIENT");
        return cause;
    }

    private static Disposition dispositionOf(Class<?> factoryClass, Method method) {
        Disposition override = METHOD_DISPOSITIONS.get(methodKey(factoryClass, method));
        if (override != null) {
            return override;
        }
        Disposition factoryLevel = FACTORY_DISPOSITIONS.get(factoryClass.getSimpleName());
        if (factoryLevel == null) {
            throw new IllegalStateException("新工厂未分类，请在处置表中登记: " + factoryClass.getSimpleName());
        }
        return factoryLevel;
    }

    private static ErrorCode expectedErrorCode(Class<?> factoryClass, Method method) {
        if (dispositionOf(factoryClass, method) != Disposition.THROWS) {
            return null;
        }
        return NON_SYSTEM_BUSY_CODES.getOrDefault(methodKey(factoryClass, method), ErrorCode.SYSTEM_BUSY);
    }

    private static String methodKey(Class<?> factoryClass, Method method) {
        return factoryClass.getSimpleName() + "#" + method.getName();
    }

    private static String describe(Class<?> factoryClass, Method method) {
        return factoryClass.getSimpleName() + "#" + method.getName() + "()";
    }

    private static long countDispositions(Map<String, Disposition> table, Disposition disposition) {
        return table.values().stream().filter(value -> value == disposition).count();
    }
}
