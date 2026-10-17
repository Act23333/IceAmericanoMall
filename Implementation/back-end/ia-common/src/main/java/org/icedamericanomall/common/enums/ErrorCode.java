package org.icedamericanomall.common.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // 系统级错误 (HTTP 状态码统一用 500)
    INTERNAL_ERROR(1000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    SYSTEM_BUSY(1001, "系统繁忙",  HttpStatus.SERVICE_UNAVAILABLE),

    // 认证/授权类错误 (HTTP 状态码用 401/403)
    UNAUTHORIZED(1011, "未登录", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(1012, "登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1013, "无权限操作", HttpStatus.FORBIDDEN),
    FREQUENT_ERROR(1014, "操作过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    ILLEGAL_REQUEST(1015, "非法请求", HttpStatus.BAD_REQUEST),
    CAPTCHA_REQUIRED(1016, "人机验证失败", HttpStatus.FORBIDDEN),
    /**
     * 账号被冻结 / 极高风险阻断 —— <b>登录链路已不再抛本码（2026-10-18 批次 ④）</b>。
     *
     * <p><b>为何退役</b>：本码原配 {@code 403}，而「密码错误」原配 {@code 200}、「账号不存在」原配
     * {@code 404} —— 三者状态码互不相同，攻击者<b>只看状态码</b>即可枚举「某账号是否存在 / 是否被冻结」。
     * 现按 {@code collaboration/09-constraints.md} §三·六「未认证 → 401」把登录失败收敛为
     * <b>唯一出口 {@link #PASSWORD_ERROR}（3007 / 401 / 中性文案）</b>，本码随之退役并纠正状态码
     * —— 即便将来有人误用，也只会得到 401，不会退化成「可区分的 403」。
     *
     * <p><b>对用户的「冻结」提示</b>只能走登录<b>成功</b>后的一次性提示或站内信，<b>不得</b>在登录失败
     * 响应里区分（否则「冻结 → 中性化」白做）。当前枚举中零生产引用（{@code AccountFreezeHandler}
     * 已改抛 3007），保留仅为兼容历史文档/契约引用。
     *
     * @deprecated 登录失败一律抛 {@link #PASSWORD_ERROR}（3007 / HTTP 401 + 中性文案）
     */
    @Deprecated(since = "batch-4-login-disclosure")
    ACCOUNT_FROZEN(1017, "用户账号异常", HttpStatus.UNAUTHORIZED),

    // 参数/资源类错误 (HTTP 状态码用 400/404)
    PARAM_ERROR(1101, "参数错误", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(1102, "用户不存在", HttpStatus.NOT_FOUND),
    NO_RESOURCE_FOUND(1103, "静态资源不存在", HttpStatus.NOT_FOUND),

    // 业务逻辑错误 (HTTP 状态码统一用 200)
    //   例外（2026-10-18 批次 ④，按 09-constraints.md §三·六「未认证 → 401」）：
    //   3007 PASSWORD_ERROR / 3008 USER_STATUS_ABNORMAL 为 401 —— 登录失败若用 200 承载，
    //   「200 vs 404」本身就是账号是否存在／是否被冻结的枚举信道。
    //   例外（2026-10-18 批次 ①，「资源不存在 = 真 404」）：本区段内 12 个 not-found 码改配 404 ——
    //   3009 / 3015 / 3016 / 3017 / 3019 / 3020 / 3032 / 3033 / 3034 / 3035 / 3036 / 3041
    //   （业务码与文案一律保留：前端仍需区分「订单不存在」与「店铺不存在」）。
    //   「业务规则拒绝」（资源存在、请求合法、规则不允许）仍是 200 信封，例如 3000 / 3010 / 3012 /
    //   3021-3024 / 3039 / 3040 / 3042 —— 两类不得混用。
    BUSINESS_EXECUTION_EXCEPTION(3000, "业务执行异常", HttpStatus.OK),
    CAPTCHA_ERROR(3001, "验证码错误", HttpStatus.OK),
    CAPTCHA_EXPIRED(3002, "验证码已过期", HttpStatus.OK),
    PHONE_ALREADY_REGISTERED(3003, "手机号已被注册", HttpStatus.OK),
    /**
     * 用户名已被占用 —— <b>死码（当前零生产引用）</b>，2026-10-18 批次 ① 仅登记不启用。
     *
     * <p><b>同语义实际走</b>：账号唯一性的拒绝出口是 {@link #PHONE_ALREADY_REGISTERED}（3003，
     * 注册主键唯一冲突）与 {@link #USER_ALREADY_EXISTS}（3006，商家/用户标识重复），
     * 「用户名」不是本平台的账号标识（登录标识为手机号/邮箱/微信），故本码没有产生点。
     *
     * <p><b>为什么不删除</b>：前后端错误码 99:99 逐条对齐门禁
     * （{@code check-error-codes.mjs}）以「后端枚举 ∖ 前端 {@code ERROR_CODE}」为判据，
     * 删除一项会让前端表多出一项而变红；且删除码值等于把「曾经存在的对外码」静默回收。
     * 保留 + 标注是最小风险处置。
     *
     * @deprecated 当前零生产引用（批次 ① 登记）；不得新增使用，账号唯一性请抛
     *         {@link #PHONE_ALREADY_REGISTERED} / {@link #USER_ALREADY_EXISTS}
     */
    @Deprecated(since = "①", forRemoval = false)
    USERNAME_ALREADY_TAKEN(3004, "用户名已被占用", HttpStatus.OK),
    BALANCE_INSUFFICIENT(3005, "余额不足", HttpStatus.OK),
    USER_ALREADY_EXISTS(3006, "用户已存在", HttpStatus.OK),
    /**
     * 登录失败 —— <b>全部失败原因的唯一对外出口</b>（2026-10-18 批次 ④，抗账号枚举）。
     *
     * <p>承载四类原因，<b>对外完全一致</b>：账号不存在 / 密码错误 / 账号状态不可登录（解绑、锁定）/
     * 极高风险阻断（原 {@link #ACCOUNT_FROZEN}）。原因细分只进服务端日志（{@code LoginFailureReason}），
     * 不进 HTTP 状态码、不进业务码、不进文案。
     *
     * <p><b>状态码为什么是 401</b>：见 {@code collaboration/09-constraints.md} §三·六
     * 「未认证 → 401」。此前本码配 {@code 200}、{@code USER_NOT_FOUND} 配 {@code 404}、
     * {@code ACCOUNT_FROZEN} 配 {@code 403} —— 状态码本身就构成「账号是否存在」的枚举信道。
     *
     * <p><b>文案为什么是中性</b>：{@code ErrorCode.message} 会在调用方未给自定义文案时成为对外
     * {@code Result.msg}，因此它自身也必须中性（调用方<b>不得</b>再传「账号不存在」「密码错误」
     * 这类可区分的自定义文案）。
     */
    PASSWORD_ERROR(3007, "账号或密码不正确", HttpStatus.UNAUTHORIZED),
    /**
     * 账号状态异常（解绑 / 锁定）—— <b>登录链路禁止使用</b>（2026-10-18 批次 ④）。
     *
     * <p>原配 {@code HttpStatus.OK}（200）：被冻结的账号登录失败返回 200 + 3008，而「账号不存在」
     * 返回 404 —— 状态码差异即可枚举账号。现按 {@code 09-constraints.md} §三·六 纠正为
     * {@code 401}（<b>安全默认</b>：即便遗漏调用也不会退化成 200），并把登录链路的抛出点改为
     * {@link #PASSWORD_ERROR}（唯一出口）。
     *
     * <p><b>使用边界（批次 ⑤b 收紧，逐字阅读）</b>：
     * <ul>
     *   <li>本码<b>仅限非登录域</b>的「账号状态异常」语义；<b>登录链路（authorization-service 登录策略
     *       / 风控处置）一律抛 {@link #PASSWORD_ERROR}</b>，否则「账号存在但不可用」会与「账号不存在」可区分。</li>
     *   <li><b>不得用于鉴权失败</b>：它在 HTTP 层是 {@code 401}（「未认证」语义）。已认证用户
     *       「状态异常 → 无权访问」属<b>403</b>（抛 {@link #FORBIDDEN}），参数非法属
     *       <b>400</b>（抛 {@link #PARAM_ERROR}）；错用本码会把一次普通的业务拒绝变成
     *       「清凭证 + 跳登录页」，冻结用户因此陷入「登录成功 → 立刻被登出」的死循环。</li>
     *   <li>当前<b>零生产引用</b>：2026-10-18 批次 ⑤b 已把两个非登录调用点分别改为
     *       {@code PARAM_ERROR(1101)}（user-service {@code UserStatusEnum#of} 管理端非法状态值）
     *       与 {@code FORBIDDEN(1013)}（{@code User#assertActive} 冻结用户访问用户资料）。
     *       保留本码仅为兼容契约/历史文档引用，<b>新增代码不应再抛它</b>。</li>
     * </ul>
     */
    USER_STATUS_ABNORMAL(3008, "用户状态异常", HttpStatus.UNAUTHORIZED),
    // 交易/订单/商品通用错误 (3009-3029)
    /**
     * 订单不存在 —— <b>资源不存在 = 真 404</b>（2026-10-18 批次 ① 状态码归位）。
     *
     * <p><b>本批改的是什么</b>：只把 {@code HttpStatus} 由 {@code OK}（200）改为
     * {@link HttpStatus#NOT_FOUND}（404），<b>业务码 3009 与文案一字未动</b>。理由：
     * <ol>
     *   <li>{@code collaboration/09-constraints.md} §三·六 第 2 条：「资源不存在 = 真 404」，
     *       只有<b>业务规则拒绝</b>（资源存在、请求合法、规则不允许）才用 200 + body code；</li>
     *   <li>前端仍需区分「订单不存在 / 店铺不存在 / SKU 不存在」—— 合并成单一 1103 会丢信息，
     *       故<b>不</b>把 12 个 not-found 码合并。</li>
     * </ol>
     *
     * <p><b>连带结论（刻意保留）</b>：{@code ErrorCategory} 按状态码派生，故本码由
     * {@code RULE} 变为 {@code CLIENT}；Feign 调用方据此<b>原样透传</b>业务码与文案
     * （§三·八·1），<b>不得</b>改写成 {@code SYSTEM_BUSY}。
     */
    ORDER_NOT_FOUND(3009, "订单不存在", HttpStatus.NOT_FOUND),
    ORDER_STATUS_INVALID(3010, "当前订单状态不支持此操作", HttpStatus.OK),
    /**
     * 商品不存在 —— <b>已被 {@link #NO_RESOURCE_FOUND}（1103 / HTTP 404）取代，禁止新增使用</b>。
     *
     * <p><b>状态码订正（2026-10-18，批次 B7b）</b>：原 {@code HttpStatus.OK} 属
     * 「用 200 信封承载 not-found」的偏离。按 {@code collaboration/09-constraints.md}
     * §三·六「错误承载的四类划分」：<b>资源不存在 = 真 404 + {@code NO_RESOURCE_FOUND(1103)}</b>；
     * 只有<b>业务规则拒绝</b>（资源存在、请求合法但规则不允许）才用 200 + body code。
     * 故本枚举的 {@code HttpStatus} 按该表纠正为 {@code NOT_FOUND}（{@code 09 §三·六}
     * 末条：「以本表为准修改该枚举的 status，不要在新代码里绕过枚举手写状态码」）。
     *
     * <p><b>为何保留而不删除</b>：契约 §二 与历史文档仍引用该码；保留但把状态纠正后，
     * 即使有遗漏调用也不会退化成「200 + 查不到」，属安全默认。新增代码一律抛
     * {@link #NO_RESOURCE_FOUND}。
     *
     * @deprecated 资源不存在统一抛 {@link #NO_RESOURCE_FOUND}（1103 / HTTP 404）；本项仅为兼容存量引用保留
     */
    @Deprecated(since = "B7b")
    PRODUCT_NOT_FOUND(3011, "商品不存在", HttpStatus.NOT_FOUND),
    STOCK_INSUFFICIENT(3012, "库存不足", HttpStatus.OK),
    /**
     * 购物车为空 —— <b>死码（当前零生产引用）</b>，2026-10-18 批次 ① 仅登记不启用。
     *
     * <p><b>同语义实际走</b>：下单前「选中的购物车为空」由
     * {@code NormalCartOrderStrategy#validate} 抛 {@link #PARAM_ERROR}（1101 / HTTP 400，
     * 「购物车为空，无法下单」）—— 那是<b>请求不可执行</b>，属调用方的参数问题，不是购物车域的错误码。
     *
     * <p><b>为什么不删除</b>：99:99 错误码对齐门禁以「后端枚举 ∖ 前端表」为判据，删一项即变红。
     *
     * @deprecated 当前零生产引用（批次 ① 登记）；不得新增使用，空购物车请抛 {@link #PARAM_ERROR}
     */
    @Deprecated(since = "①", forRemoval = false)
    CART_EMPTY(3013, "购物车为空", HttpStatus.OK),
    /**
     * 暂不支持跨店下单 —— <b>死码（当前零生产引用）</b>，2026-10-18 批次 ① 仅登记不启用。
     *
     * <p><b>同语义实际走</b>：{@code NormalCartOrderStrategy#validate} 对「一单多商家」抛
     * {@link #BUSINESS_EXECUTION_EXCEPTION}（3000 / HTTP 200，「暂不支持跨店下单，请分别结算」）
     * —— 属<b>业务规则拒绝</b>（资源都在、请求合法、规则不允许），按 {@code 09-constraints}
     * §三·六 就该用 200 信封而不是独立码。
     *
     * @deprecated 当前零生产引用（批次 ① 登记）；不得新增使用，跨店下单请抛
     *         {@link #BUSINESS_EXECUTION_EXCEPTION}
     */
    @Deprecated(since = "①", forRemoval = false)
    CROSS_STORE_FORBIDDEN(3014, "暂不支持跨店下单", HttpStatus.OK),
    /**
     * 店铺<b>不存在</b> —— <b>仅表示资源不存在</b>，不得用于「状态不允许」，也不得用于「越权」。
     *
     * <p><b>语义拆分（批次 ③「拆双义错误码」，2026-10-18）</b>：本码此前同时承载三种语义，
     * 现按 {@code collaboration/09-constraints.md} §三·六「错误承载的四类划分」逐条归位：
     * <table border="1">
     *   <caption>拆出去的两种语义</caption>
     *   <tr><th>原语义</th><th>归到哪</th><th>判据（09 §三·六）</th></tr>
     *   <tr>
     *     <td>店铺存在但<b>状态不允许</b>（{@code PENDING} 审核中 / {@code FROZEN} 冻结）</td>
     *     <td>{@link #BUSINESS_EXECUTION_EXCEPTION}（3000）+ 自定义文案，HTTP 200</td>
     *     <td><b>业务规则拒绝</b>：资源存在、请求合法，规则不允许 → 200 + body code（{@code RULE}）</td>
     *   </tr>
     *   <tr>
     *     <td><b>店铺归属其他商家</b>（已认证但无权访问）</td>
     *     <td>{@link #FORBIDDEN}（1013），HTTP 403</td>
     *     <td><b>无权限</b> → 真 403。这是既有实现（{@code ShopManager#loadOwnedShop}），
     *         本批<b>未</b>用 not-found 掩盖，故<b>不存在</b>「反枚举取舍」待裁决项</td>
     *   </tr>
     * </table>
     *
     * <p><b>状态码已归位（批次 ①，2026-10-18）</b>：{@code HttpStatus.OK} → {@link HttpStatus#NOT_FOUND}。
     * ③ 只拆语义、不动状态码；① 只改状态码、不再动调用点 —— 两步分开执行的理由是
     * 「若在拆分的同时改 404，会把上表刚拆出去的『状态不允许』等分支一起变成 404」。
     * 现在 3015 只承载上表第 1 行（资源不存在），因此可以安全 404。
     *
     * <p><b>安全默认</b>：即便有人新增调用点，返回值也只是「404 + 店铺不存在」，
     * 不会退化成「200 + 状态不允许」这种被拆掉的错误语义。
     */
    SHOP_NOT_FOUND(3015, "店铺不存在", HttpStatus.NOT_FOUND),
    /**
     * SKU <b>不存在</b> —— <b>仅表示资源不存在</b>，<b>不得</b>用于「业务键形态非法」。
     *
     * <p><b>语义拆分（批次 ③，2026-10-18）</b>：本码此前把两件事写成同一个异常 ——
     * 「SKU 不存在」与「业务键拼错（长度/字符集错）」。后者是<b>参数非法</b>，
     * 按 §三·六 属 <b>{@code CLIENT}</b>：<b>HTTP 400 + {@link #PARAM_ERROR}（1101）</b>，
     * 且<b>校验前移到适配层 fail-fast</b>（{@code IdGenerator#requireWire}，
     * ADR-062 §决策 7「编解码唯一出口 + 形态非法 400」）：
     * <ul>
     *   <li>购物车：{@code CartController} 的 C2/C3 请求体与 C4/C5 路径变量；</li>
     *   <li>库存：{@code InternalSkuController#deductStock}（I14）的请求体。</li>
     * </ul>
     * 应用层（{@code CartManager}/{@code SkuManager}）保留同口径的<b>第二道防线</b>：
     * 形态非法同样抛 400 + 1101，<b>绝不</b>把「拼错一个 hex 字符」降级成「SKU 不存在」
     * （ADR-062 §决策 7：那会把排查成本全部转嫁给调用方）。
     *
     * <p><b>状态码已归位（批次 ①，2026-10-18）</b>：{@code HttpStatus.OK} → {@link HttpStatus#NOT_FOUND}。
     * 拆分后 3016 只承载「形态合法但库里没有」，故可以安全 404；形态非法那支在 ③ 已前移到
     * 适配层 fail-fast（400 + 1101），<b>不会</b>被本状态码变更波及。
     */
    SKU_NOT_FOUND(3016, "SKU不存在", HttpStatus.NOT_FOUND),
    /**
     * 首页配置不存在 —— 资源不存在 = 真 404（批次 ①：只改状态码，业务码与文案不变）。
     */
    HOME_CONFIG_NOT_FOUND(3017, "首页配置不存在", HttpStatus.NOT_FOUND),
    REVIEW_ALREADY_EXISTS(3018, "该商品已评价", HttpStatus.OK),
    /**
     * 优惠券不存在 —— 资源不存在 = 真 404（批次 ①）。
     *
     * <p>调用点含「非本店券按不存在处理」的<b>反枚举掩盖</b>（{@code CouponAdminManager}）：
     * 该口径在 404 下依然成立（他人券与不存在的券状态码、业务码、文案三者同形），未放宽。
     */
    COUPON_NOT_FOUND(3019, "优惠券不存在", HttpStatus.NOT_FOUND),
    // 秒杀错误 (3020-3029)
    /**
     * 秒杀活动不存在 —— 资源不存在 = 真 404（批次 ①）。
     *
     * <p>与 {@link #FLASH_SALE_NOT_STARTED}（3021，场次存在但不在时间窗内）严格区分：
     * 后者是<b>业务规则拒绝</b>，仍是 200 信封，没有被本批波及。
     */
    FLASH_SALE_NOT_FOUND(3020, "秒杀活动不存在", HttpStatus.NOT_FOUND),
    FLASH_SALE_NOT_STARTED(3021, "秒杀未开始或已结束", HttpStatus.OK),
    FLASH_SALE_SOLD_OUT(3022, "已售罄", HttpStatus.OK),
    FLASH_SALE_LIMIT_EXCEEDED(3023, "每人限购1件", HttpStatus.OK),
    FLASH_SALE_FAILED(3024, "抢购失败，请重试", HttpStatus.OK),
    // 售后/申请 (3030-3039)
    /**
     * 已有审核中的申请 —— <b>死码（当前零生产引用）</b>，2026-10-18 批次 ① 仅登记不启用。
     *
     * <p><b>同语义实际走</b>：{@link #AFTER_SALE_EXISTS}（3031，「已有售后申请」，
     * {@code AfterSaleManager} 的售后去重出口）。本码是历史「商家入驻申请 / 提现申请」时代的
     * 遗留项，两条链路现均已下沉到各自聚合的重复提交守卫。
     *
     * @deprecated 当前零生产引用（批次 ① 登记）；不得新增使用，请抛 {@link #AFTER_SALE_EXISTS}
     *         或对应域的重复提交守卫
     */
    @Deprecated(since = "①", forRemoval = false)
    APPLICATION_EXISTS(3030, "已有审核中的申请", HttpStatus.OK),
    AFTER_SALE_EXISTS(3031, "已有售后申请", HttpStatus.OK),
    /**
     * 申请不存在 —— 资源不存在 = 真 404（批次 ①）。
     *
     * <p>承载「售后申请不存在」（{@code AfterSaleManager#review} 的 {@code requireAfterSale}）。
     * 同族的 {@code confirmReturn} / {@code markRefunded} 走 {@link #NO_RESOURCE_FOUND}，
     * 两者在 HTTP 层现已同为 404（此前 200 vs 404 不一致，本批顺带收敛）。
     */
    APPLICATION_NOT_FOUND(3032, "申请不存在", HttpStatus.NOT_FOUND),
    /**
     * 提现申请不存在 —— 资源不存在 = 真 404（批次 ①）。
     */
    WITHDRAWAL_NOT_FOUND(3033, "提现申请不存在", HttpStatus.NOT_FOUND),
    // 通知/物流 (3034-3035)
    /**
     * 通知不存在 —— 资源不存在 = 真 404（批次 ①）。
     *
     * <p>调用点（{@code InAppMessageManager#markRead}）把「不存在」与「不属于本人」并成同一出口
     * —— 站内信是用户私有资源，掩盖他人资源的存在性属既有反枚举口径，本批<b>未</b>改动该口径，
     * 只改状态码（掩盖在 404 下依然成立：两者状态码、业务码、文案同形）。
     */
    NOTIFICATION_NOT_FOUND(3034, "通知不存在", HttpStatus.NOT_FOUND),
    /**
     * 物流信息不存在 —— 资源不存在 = 真 404（批次 ①）。
     */
    LOGISTICS_NOT_FOUND(3035, "物流信息不存在", HttpStatus.NOT_FOUND),
    /**
     * 评价不存在 —— <b>死码（当前零生产引用）</b>，2026-10-18 批次 ① 仅登记、只改状态码。
     *
     * <p><b>同语义实际走</b>：改造前 {@code ReviewManager#replyToReview} 抛的是
     * {@link #BUSINESS_EXECUTION_EXCEPTION}（3000）；批次 ① 已把它换成
     * {@link #NO_RESOURCE_FOUND}（1103 / 404），与本码同语义、同状态码。
     *
     * <p><b>为什么只标注不启用、也不删除</b>：99:99 错误码门禁以「后端枚举 ∖ 前端表」为判据，
     * 删除即变红；而「换用 3036」等于在 1103 已统一 not-found 之后再造一个同义码 —— 属回退。
     * 因此保持「零引用 + 404」，作为一个不会误伤的安全默认项。
     *
     * @deprecated 当前零生产引用（批次 ① 登记，forRemoval=false）；评价不存在请抛
     *         {@link #NO_RESOURCE_FOUND}（1103 / HTTP 404）
     */
    @Deprecated(since = "①", forRemoval = false)
    REVIEW_NOT_FOUND(3036, "评价不存在", HttpStatus.NOT_FOUND),
    /**
     * 该评价已回复 —— <b>死码（当前零生产引用）</b>，2026-10-18 批次 ① 仅登记不启用。
     *
     * <p><b>同语义实际走</b>：{@code ReviewManager#replyToReview} 在条件更新未命中时抛
     * {@link #BUSINESS_EXECUTION_EXCEPTION}（3000 / 200，「已回复过该评价」）—— 属<b>业务规则拒绝</b>，
     * 不是资源不存在，故本批<b>不</b>把它改成 404。
     *
     * @deprecated 当前零生产引用（批次 ① 登记）；不得新增使用
     */
    @Deprecated(since = "①", forRemoval = false)
    REVIEW_ALREADY_REPLIED(3037, "该评价已回复", HttpStatus.OK),
    /**
     * 追评失败 —— <b>死码（当前零生产引用）</b>，2026-10-18 批次 ① 仅登记不启用。
     *
     * <p><b>同语义实际走</b>：{@code ReviewManager#appendReview} 的条件更新把三种原因
     * （评价不存在 / 非本人 / 已追评）并在一条 3000 上抛出。本批<b>不</b>拆这条多义出口
     * （不在 ① 的清单内），故本码仍为零引用。
     *
     * @deprecated 当前零生产引用（批次 ① 登记）；不得新增使用
     */
    @Deprecated(since = "①", forRemoval = false)
    REVIEW_APPEND_CONFLICT(3038, "追评失败", HttpStatus.OK),
    REFUND_STATUS_INVALID(3039, "退款状态不支持此操作", HttpStatus.OK),
    FREIGHT_TEMPLATE_NAME_EXISTS(3040, "运费模板名称已存在", HttpStatus.OK),
    /**
     * 运费模板不存在 —— 资源不存在 = 真 404（批次 ①）。
     *
     * <p>调用点（{@code FreightTemplateManager#requireOwned} / {@code FreightTemplate#assertOwnedBy}）
     * 刻意<b>不区分</b>「模板不存在」与「模板属于他人」，统一本码 —— 属既有反枚举口径，
     * 本批未改动（404 下掩盖更彻底：两种情形的状态码、业务码、文案三者同形）。
     */
    FREIGHT_TEMPLATE_NOT_FOUND(3041, "运费模板不存在", HttpStatus.NOT_FOUND),
    /**
     * 购物车条目数达上限（B7b 新增）—— <b>业务规则拒绝</b>：资源存在、请求合法，
     * 只是条目数不允许再增，故按 09 §三·六 用 200 + body code（不是 4xx）。
     *
     * <p>上限值集中在 {@code common.constant.CsvIdLimits#MAX_CART_ITEMS}
     * （按「下单请求体预算」这条判据倒推，不按历史习惯值）。
     */
    CART_ITEM_LIMIT_EXCEEDED(3042, "购物车条目数已达上限", HttpStatus.OK),

    // AI 服务错误 (3100-3199)
    AI_SERVICE_UNAVAILABLE(3100, "AI 服务未启用或 API Key 未配置", HttpStatus.SERVICE_UNAVAILABLE),
    AI_TIMEOUT(3101, "AI 调用超时，请稍后重试", HttpStatus.GATEWAY_TIMEOUT),
    AI_CONTENT_FILTERED(3102, "内容未通过安全审核", HttpStatus.BAD_REQUEST),
    AI_RATE_LIMITED(3103, "AI 助手正在忙碌中，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    AI_CONVERSATION_NOT_FOUND(3104, "会话不存在或无权访问", HttpStatus.NOT_FOUND),

    // ============================================================
    // 第三方接口访问错误（4000-4999）
    // 设计原则：
    //   4000-4009：通用第三方错误（所有厂商共用）
    //   4010-4019：微信相关（登录、支付、消息）
    //   4020-4029：支付宝相关
    //   4030-4039：短信/邮件服务商
    //   4040-4049：OSS/文件存储服务商
    //   4050-4059：地图/物流等
    //   4999：未分类的第三方错误兜底
    // ============================================================

    // ---------- 通用第三方错误（4000-4009） ----------
    THIRD_PARTY_ERROR(4000, "第三方服务异常，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE),
    THIRD_PARTY_TIMEOUT(4001, "第三方服务响应超时", HttpStatus.GATEWAY_TIMEOUT),
    THIRD_PARTY_CONNECT_FAILED(4002, "第三方服务连接失败", HttpStatus.SERVICE_UNAVAILABLE),
    THIRD_PARTY_RATE_LIMITED(4003, "第三方服务调用频率超限", HttpStatus.TOO_MANY_REQUESTS),
    THIRD_PARTY_AUTH_FAILED(4004, "第三方服务认证失败", HttpStatus.UNAUTHORIZED),
    THIRD_PARTY_SIGN_ERROR(4005, "第三方接口签名验证失败", HttpStatus.BAD_REQUEST),
    THIRD_PARTY_PARAM_ERROR(4006, "第三方接口参数错误", HttpStatus.BAD_REQUEST),
    THIRD_PARTY_RESPONSE_PARSE_ERROR(4007, "第三方响应数据解析失败", HttpStatus.INTERNAL_SERVER_ERROR),
    THIRD_PARTY_UNKNOWN_ERROR(4008, "第三方未知错误", HttpStatus.INTERNAL_SERVER_ERROR),

    // ---------- 微信相关（4010-4019） ----------
    WECHAT_ERROR(4010, "微信服务异常", HttpStatus.SERVICE_UNAVAILABLE),
    WECHAT_CODE_INVALID(4011, "微信登录 code 无效或已过期", HttpStatus.BAD_REQUEST),
    WECHAT_AUTH_FAILED(4012, "微信授权失败", HttpStatus.UNAUTHORIZED),
    WECHAT_OPENID_NOT_FOUND(4013, "微信用户信息不存在", HttpStatus.NOT_FOUND),
    WECHAT_PAY_ERROR(4014, "微信支付调用失败", HttpStatus.SERVICE_UNAVAILABLE),
    WECHAT_PAY_SIGN_ERROR(4015, "微信支付签名验证失败", HttpStatus.BAD_REQUEST),
    WECHAT_PAY_ORDER_NOT_FOUND(4016, "微信支付订单不存在", HttpStatus.NOT_FOUND),
    WECHAT_REFUND_ERROR(4017, "微信退款失败", HttpStatus.SERVICE_UNAVAILABLE),
    WECHAT_REFUND_AMOUNT_ERROR(4018, "微信退款金额不合法", HttpStatus.BAD_REQUEST),

    // ---------- 支付宝相关（4020-4029） ----------
    ALIPAY_ERROR(4020, "支付宝服务异常", HttpStatus.SERVICE_UNAVAILABLE),
    ALIPAY_AUTH_FAILED(4021, "支付宝授权失败", HttpStatus.UNAUTHORIZED),
    ALIPAY_PAY_ERROR(4022, "支付宝支付调用失败", HttpStatus.SERVICE_UNAVAILABLE),
    ALIPAY_SIGN_ERROR(4023, "支付宝签名验证失败", HttpStatus.BAD_REQUEST),
    ALIPAY_REFUND_ERROR(4024, "支付宝退款失败", HttpStatus.SERVICE_UNAVAILABLE),
    ALIPAY_TRADE_NOT_FOUND(4025, "支付宝交易不存在", HttpStatus.NOT_FOUND),

    // ---------- 短信/邮件服务商（4030-4039） ----------
    SMS_SEND_FAILED(4030, "短信发送失败", HttpStatus.SERVICE_UNAVAILABLE),
    SMS_RATE_LIMITED(4031, "短信发送过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    SMS_TEMPLATE_ERROR(4032, "短信模板不存在或未审核", HttpStatus.BAD_REQUEST),
    SMS_SIGN_ERROR(4033, "短信签名不合法", HttpStatus.BAD_REQUEST),
    SMS_BALANCE_INSUFFICIENT(4034, "短信服务商余额不足", HttpStatus.SERVICE_UNAVAILABLE),
    EMAIL_SEND_FAILED(4035, "邮件发送失败", HttpStatus.SERVICE_UNAVAILABLE),

    // ---------- OSS/文件存储（4040-4049） ----------
    OSS_UPLOAD_FAILED(4040, "文件上传失败", HttpStatus.SERVICE_UNAVAILABLE),
    OSS_DOWNLOAD_FAILED(4041, "文件下载失败", HttpStatus.SERVICE_UNAVAILABLE),
    OSS_DELETE_FAILED(4042, "文件删除失败", HttpStatus.SERVICE_UNAVAILABLE),
    OSS_FILE_NOT_FOUND(4043, "文件不存在", HttpStatus.NOT_FOUND),
    OSS_FILE_TOO_LARGE(4044, "文件大小超出限制", HttpStatus.BAD_REQUEST),
    OSS_FILE_TYPE_NOT_ALLOWED(4045, "文件类型不允许", HttpStatus.BAD_REQUEST),

    // ---------- 地图/物流等（4050-4059） ----------
    MAP_SERVICE_ERROR(4050, "地图服务异常", HttpStatus.SERVICE_UNAVAILABLE),
    MAP_GEOCODE_FAILED(4051, "地址解析失败", HttpStatus.BAD_REQUEST),
    LOGISTICS_QUERY_FAILED(4052, "物流信息查询失败", HttpStatus.SERVICE_UNAVAILABLE),
    LOGISTICS_COMPANY_NOT_FOUND(4053, "物流公司不存在", HttpStatus.NOT_FOUND),

    // ---------- 兜底 ----------
    THIRD_PARTY_UNDEFINED(4999, "未分类的第三方错误", HttpStatus.INTERNAL_SERVER_ERROR),

    // ----------通用加密解密工具错误（5000-5020----------
    ENCRYPT_ERROR(5000, "加密错误", HttpStatus.INTERNAL_SERVER_ERROR),
    DECRYPT_ERROR(5001, "解密错误", HttpStatus.INTERNAL_SERVER_ERROR),
    HASH_ERROR(5002, "哈希错误", HttpStatus.INTERNAL_SERVER_ERROR),

    ;


    private final int code;          // 业务码
    private final String message;    // 默认提示信息
    private final HttpStatus httpStatus; // HTTP 状态码

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}