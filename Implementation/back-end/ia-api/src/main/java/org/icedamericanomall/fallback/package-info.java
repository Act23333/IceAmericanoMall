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
 * 当前仅 {@code authorization-service} 开启了熔断。
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
 * <h2>2.1 横向铁律：4xx 透传，只有 5xx/超时/熔断才降级（§三·八·1）</h2>
 * 上述三分类回答「返回什么」，但<b>不回答</b>「下游明确说了业务失败时该不该改写」。按
 * {@code collaboration/09-constraints.md} §三·八·1：下游 <b>4xx + 已知 code 必须原样透传</b>，
 * 只有 5xx / 超时 / 熔断才允许改写成本域的 {@code SYSTEM_BUSY}。降级工厂拿到的 {@code cause}
 * 里若有 {@code org.icedamericanomall.exception.UpstreamException} 且 {@code category == CLIENT}，
 * <b>必须原样抛出它</b>（保留下游业务码/文案），不得改成 {@code SYSTEM_BUSY}——
 * 反例正是 {@code SpuDetailClient}：把下游 404「商品不存在」说成「商品服务暂不可用」。
 * 另外 §三·八·2 要求「只读展示类 fail-open 时的文案不得写成『服务不可用』」，
 * 要说「<b>暂时无法获取</b>」。
 *
 * <p><b>迁移状态（2026-10-18 批次 ⑤）</b>：{@code SpuDetailClientFallback} 已按上述铁律落地
 * （4xx 透传 / 其余降级）。其余 18 个工厂仍是无条件 {@code SYSTEM_BUSY} 的旧口径 ——
 * 对它们的调用链（写/资金/库存为主）当前语义正确（fail-closed），故本批<b>未</b>扩大改动面；
 * 待各自出现「下游 4xx 被改写成故障」的实测案例时按同一模式迁移。
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
 * <h2>4. 逐 Client 降级语义速查表</h2>
 * <table border="1">
 *   <caption>全量 Client 降级语义</caption>
 *   <tr>
 *     <th>Client</th><th>方法</th><th>类别</th><th>降级行为</th>
 *   </tr>
 *   <tr><td rowspan="3">CartClient</td><td>getSelectedItems</td><td>② 判别型</td><td>抛 BizException</td></tr>
 *   <tr><td>clearCart</td><td>③ 命令</td><td>抛 BizException</td></tr>
 *   <tr><td>deleteByIds</td><td>③ 命令</td><td>抛 BizException</td></tr>
 *   <tr><td rowspan="3">OrderClient</td><td>updateOrderStatus</td><td>③ 命令</td><td>抛 BizException</td></tr>
 *   <tr><td>getOrder</td><td>② 判别型</td><td>抛 BizException</td></tr>
 *   <tr><td>createOrder</td><td>③ 命令</td><td>抛 BizException</td></tr>
 *   <tr><td>SkuClient</td><td>getSkuListByIds</td><td>② 判别型</td><td>抛 BizException</td></tr>
 *   <tr><td>SkuClient</td><td>deductStock / restoreStock</td><td>③ 命令</td><td>抛 BizException</td></tr>
 *   <tr><td>SpuDetailClient</td><td>getSpuDetail</td><td>② 判别型</td>
 *       <td><b>4xx 透传 UpstreamException</b> / 5xx·超时·熔断 → 抛 BizException(SYSTEM_BUSY)</td></tr>
 *   <tr><td>SearchClient</td><td>searchSpus</td><td>① 展示型</td><td>非成功 Result（data=null）</td></tr>
 *   <tr><td>UserProfileClient</td><td>getBrowseHistory / getFavorites</td><td>① 展示型</td><td>Result.ok(空列表)</td></tr>
 *   <tr><td>NotificationClient</td><td>sendSms / sendEmail / sendInAppMessage</td><td>③ 命令</td><td>抛 BizException</td></tr>
 *   <tr><td>VerificationClient</td><td>issueCode / verifyCode / verifyCaptcha</td><td>② 判别型（安全优先）</td><td>结构化 success=false</td></tr>
 *   <tr><td>AddressClient / BalanceClient / CouponClient / PointsClient / LogisticsClient / UserClient</td>
 *       <td>（详见各自实现）</td><td>混合</td><td>查询→空值；命令→异常</td></tr>
 * </table>
 *
 * <p><b>V5.2</b>：认证域 Feign 契约（{@code /internal/auth/**}）已随认证边界收敛整体废弃
 * （ADR-017 —— 端点、user-service 对端与共享 CO 一并移除），本包不再包含其降级工厂。
 *
 * @see org.springframework.cloud.openfeign.FallbackFactory
 */
package org.icedamericanomall.fallback;
