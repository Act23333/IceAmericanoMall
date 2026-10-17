/**
 * Feign 降级工厂（FallbackFactory）统一规范。
 *
 * <h2>1. 生效前提（重要）</h2>
 * {@code @FeignClient(fallbackFactory = ...)} <b>只有在该服务的
 * {@code spring.cloud.openfeign.circuitbreaker.enabled=true} 时才真正生效</b>
 * （同时需要 {@code spring-cloud-starter-circuitbreaker-resilience4j} 依赖）。
 * 未开启熔断时，Feign 直接抛 {@code FeignException}，本包的降级实现<b>不会</b>被调用。
 *
 * <p>这是刻意的分层约定：<b>是否启用熔断是各服务自己的运行时决策</b>（取决于它对下游可用性的
 * 容忍度），ia-api 只负责把「契约侧的降级语义」声明清楚，不替服务做该决策。
 * <b>当前开启情况（ADR-011 / ADR-022）</b>：{@code authorization-service} /
 * {@code user-service} / {@code merchant-service} / {@code verification-service} /
 * {@code marketing-service} / {@code ai-service} / {@code pay-service} /
 * {@code logistics-service} 均已开启；{@code search-service} 随后按 ADR-052 为
 * {@code ItemSearchClient}（reindex 数据源 / DB 兜底检索）开启；
 * {@code trade-service} 不启用 resilience4j，改用 Sentinel（{@code feign.sentinel.enabled=true}，
 * 规则见 ADR-028）。
 * <b>真相源是各服务自己的 {@code application.yml}</b>——本文件不复制逐个服务的开关清单
 * （会漂，且漂了没人会发现）。
 *
 * <p>第二个前提：{@code fallbackFactory} 指定的类必须是<b>容器里的 Bean</b>。
 * 因此启用熔断的服务，其 {@code @SpringBootApplication(scanBasePackages = ...)}
 * 必须包含 {@code org.icedamericanomall.fallback}，否则启动期报
 * {@code No fallback instance of type ... found}。
 * 参考 {@code verification-service} / {@code authorization-service} 的启动类写法。
 *
 * <h2>2. 降级语义三分法</h2>
 * 降级不是「随便返回个默认值」，而是<b>必须让调用方拿到与真实故障一致的语义</b>。
 * 本包统一按下列三类选择策略，每个 Client 的 Javadoc 必须写明它属于哪一类及理由。
 *
 * <table border="1">
 *   <caption>降级语义分类</caption>
 *   <tr>
 *     <th>类别</th><th>判定标准</th><th>策略</th>
 *   </tr>
 *   <tr>
 *     <td><b>① 展示型查询</b></td>
 *     <td>返回值为空时，调用方只做「少展示一点」，不会据此做出错误的业务判断或写操作</td>
 *     <td>返回空集合 / {@code null} / 非成功 {@code Result} + {@code WARN} 日志</td>
 *   </tr>
 *   <tr>
 *     <td><b>② 判别型查询</b></td>
 *     <td>返回值为空会被调用方解释为一条<b>业务事实</b>（如「购物车为空」「SKU 不存在」），
 *         从而向用户输出误导性提示，或掩盖下游故障</td>
 *     <td>抛 {@code BizException}（fail-closed），让调用方走「系统繁忙」分支</td>
 *   </tr>
 *   <tr>
 *     <td><b>③ 命令（写）</b></td>
 *     <td>任何状态变更：落库、扣减、投递、状态流转</td>
 *     <td>一律抛 {@code BizException}，<b>禁止</b>静默成功（静默即数据不一致）</td>
 *   </tr>
 * </table>
 *
 * <p>分类之所以强调「判别型查询」单独成类，是因为实践中它是最容易被写错的一类：
 * 例 {@code CartClient#getSelectedItems} 降级返回空列表后，下单流程会向用户报
 * 「购物车为空，无法下单」——用户明明有商品，故障却伪装成了业务事实，属于典型的
 * <b>错误信息污染</b>。此类一律按 ② 处理。
 *
 * <p>三分法按<b>方法</b>定，不是按类定：同一个工厂里既有必须抛错的方法（命令 / 判别型）
 * 也有可以返回降级值的方法（展示型）时，逐方法分别处置（ADR-011）。因此本包不存在
 * 「一个工厂一个结论」的简化假设 —— 语义门禁（§2.3）也是逐方法验证的。
 *
 * <h2>2.1 横向铁律：4xx 透传，只有 5xx/超时/熔断才降级（§三·八·1）</h2>
 * 上述三分类回答「返回什么」，但<b>不回答</b>「下游明确说了业务失败时该不该改写」。按
 * {@code collaboration/09-constraints.md} §三·八·1：下游 <b>4xx + 已知 code 必须原样透传</b>，
 * 只有 5xx / 超时 / 熔断才允许改写成本域的 {@code SYSTEM_BUSY}。降级工厂拿到的 {@code cause}
 * 里若有 {@code org.icedamericanomall.exception.UpstreamException} 且 {@code category == CLIENT}，
 * <b>必须原样抛出它</b>（保留下游业务码/文案/traceId），不得改成 {@code SYSTEM_BUSY}——
 * 反例是「把下游 404『商品不存在』说成『商品服务暂不可用』」。
 * 另外 §三·八·2 要求「只读展示类 fail-open 时的文案不得写成『服务不可用』」，
 * 要说「<b>暂时无法获取</b>」。
 *
 * <p><b>例外只能显式登记，不能默默放宽</b>：少数场景（纯旁路增强的推荐/画像类查询、
 * 以及「事务已成功之后的状态回写」）会显式豁免透传 —— 逐条的判定理由写在<b>各自类注释</b>里
 * （依据是调用点的时序与 fail-open 纪律），并由 §2.3 的语义门禁逐方法兜住。
 * 「这个类比较特殊」不是理由；不登记就无法通过门禁。
 *
 * <p><b>当前实现状态（2026-10-18）</b>：本包目前 31 个降级工厂，<b>全部</b>按
 * {@code category} 分流（4xx 透传 / 其余按各自三分法处置），不再存在「无条件
 * {@code SYSTEM_BUSY}」的旧口径。其中 {@code TradeInvoiceClientFallback} 已从
 * <b>静默 {@code void} 返回</b>改为<b>抛出</b>（调用侧在 {@code catch} 内落补偿记录
 * {@code invoice_link_compensation}），因为静默成功会让调用方打出「已联动冲红」而实际漏冲红。
 * 「31」这个数字不写死在任何门禁里（门禁只要求「≥30 且逐个登记」），
 * 以 {@code FallbackDegradeContractTest} 的发现输出为准。
 *
 * <h2>2.2 类别判定的唯一入口（2026-10-18 批次 ⑤b）</h2>
 * 「这条 {@code cause} 属于 CLIENT / RULE / TRANSIENT / BUG」<b>只允许问一个地方</b>：
 * {@code org.icedamericanomall.exception.UpstreamFailures#classify}。它与
 * {@code UpstreamErrorDecoder} 共用同一判据，顺序钉死为
 * <b>异常类型 → HTTP 状态码 → 500 才是 BUG</b>：
 * <ul>
 *   <li><b>异常类型优先</b>：{@code feign.RetryableException} / {@code ConnectException} /
 *       {@code SocketTimeoutException} / {@code IOException} / {@code TimeoutException} /
 *       resilience4j {@code CallNotPermittedException}（熔断打开）/ {@code BulkheadFullException} /
 *       Sentinel {@code BlockException} → {@code TRANSIENT}。这一类<b>没有 HTTP 响应</b>，
 *       只看状态码永远判不出来，且一旦被误判成 {@code BUG} 就<b>不可重试</b>；</li>
 *   <li><b>已类型化的 {@code UpstreamException}</b> 携带下游类别时原样采信（4xx 透传的基础）；</li>
 *   <li><b>状态码</b>：4xx → {@code CLIENT}、2xx → {@code RULE}、408/429/5xx-infra →
 *       {@code TRANSIENT}、<b>500 且无异常线索 → {@code BUG}</b>。</li>
 * </ul>
 * <b>禁止</b>在降级工厂里自己 {@code instanceof} 判断异常类型、或手写一份状态码映射 ——
 * 那正是「解码器一套、降级一套」的来源：同一枚故障会被两个结论（如解码器说 TRANSIENT、
 * 降级工厂说 BUG）左右调用方的重试决策。这一条有结构门禁兜底，见 §2.3。
 *
 * <h2>2.3 强制手段（真源）—— 本文件不写逐类清单</h2>
 * 降级语义的<b>真源是两只机械门禁</b>，不是注释里的表格：注释复写清单必然漂移
 * （本项目已因「注释/文档抄一份会变的清单」出过多次事故：枚举值、字段名、路径、计数）。
 * 想确认某个工厂的实际语义，请看代码与门禁，不要在这里找表。
 *
 * <ol>
 *   <li><b>结构层：{@code architecture-tests} 的 {@code FallbackCategoryGuardTest}</b> ——
 *       规则「位于 {@code org.icedamericanomall.fallback..} 的降级工厂，只要依赖
 *       {@code BizException}（即会抛错），就必须同时依赖
 *       {@code org.icedamericanomall.exception.UpstreamFailures}」。
 *       豁免表（类全名 → 理由）必须<b>陈旧即失败</b>：登记项对应的类必须真实存在、且仍然违规，
 *       否则测试判红 —— 白名单不会退化成免责名单。
 *       该守卫自带<b>变异验证</b>（一个「会抛 {@code BizException} 却不引用
 *       {@code UpstreamFailures}」的样本必须被判红），确保它不是一条永远为真的空规则。</li>
 *   <li><b>语义层：{@code ia-api} 的 {@code FallbackDegradeContractTest}</b> ——
 *       扫描 {@code target/classes} 发现<b>全部</b>工厂（不维护类名清单），逐个方法验证：
 *       下游 4xx（{@code CLIENT}）<b>不得</b>被改写成 {@code BizException}；
 *       下游不可用（{@code TRANSIENT}）时按<b>人工判定</b>的处置表执行 ——
 *       {@code THROWS}（必须抛 {@code BizException}，错误码按登记口径）或
 *       {@code RETURNS}（必须返回降级值、不抛）。<b>新工厂未在处置表登记即红</b>，
 *       登记项对应的类/方法消失也红（陈旧条目）。处置表刻意<b>不用</b>「有没有 import
 *       {@code BizException}」自动推导 —— 那样把 fail-closed 改成 fail-open 的实现
 *       会连带把表改对，门禁就失去意义。</li>
 * </ol>
 *
 * <p>结论：<b>改降级语义 = 同时改实现与处置表/豁免表</b>，并在评审里说明为什么。
 * 反过来，本文件只负责讲清「原则与为什么」，不承担「记录当前事实」的职责。
 *
 * <h2>3. 统一实现约定</h2>
 * <ul>
 *   <li>实现 {@link org.springframework.cloud.openfeign.FallbackFactory}（而非
 *       {@code Fallback}），以便拿到 {@code cause} 并打印真实故障栈。</li>
 *   <li>类上标注 {@code @Slf4j} + {@code @Component}。</li>
 *   <li>{@code create(...)} 内用匿名内部类实现<b>全部</b>接口方法；新增方法是编译期错误，
 *       避免漏写导致 {@code UnsupportedOperationException} 被吞。</li>
 *   <li>日志一律 {@code ERROR} 级别（降级是可观测性事件，WARN 不足以触发告警），
 *       并带上足以定位的关键业务键（订单号 / 用户 ID / SKU ID）。</li>
 *   <li>日志禁止打印完整手机号 / 邮箱 / 身份证（见 {@code 09-constraints} 脱敏要求）。</li>
 *   <li>抛出的异常必须使用 {@code ia-common} 的 {@code ErrorCode}，
 *       禁止裸 {@code RuntimeException}。</li>
 * </ul>
 *
 * <p><b>V5.2</b>：认证域 Feign 契约（{@code /internal/auth/**}）已随认证边界收敛整体废弃
 * （ADR-017 —— 端点、user-service 对端与共享 CO 一并移除），本包不再包含其降级工厂。
 *
 * @see org.springframework.cloud.openfeign.FallbackFactory
 * @see org.icedamericanomall.exception.UpstreamFailures
 */
package org.icedamericanomall.fallback;
