package org.icedamericanomall.trade.domain.port;

/**
 * 出款单号工厂（**纯函数契约**）。
 *
 * <p>用专用接口而不是 {@code IntFunction<String>}：后者只带序号，拿不到提现单号；
 * 而专用接口能把"纯函数"这条约束写在类型上。
 *
 * <h2>实现必须满足</h2>
 * <ul>
 *   <li><b>不得捕获可变状态</b>：必须是 {@code (withdrawalNo, attemptNo) → 单号} 的纯映射。
 *       注意 ArchUnit **兜不住 lambda**（捕获的变量在字节码里变成合成字段），
 *       所以这条靠 domain 层的 {@code final} 静态实现 + 工厂抛异常时的回滚用例共同保证；</li>
 *   <li><b>同一入参必须得到同一单号</b>：否则幂等键会漂移，渠道去重直接失效；</li>
 *   <li><b>不得做 I/O</b>：它会在持有提现单行锁的临界区里被调用。</li>
 * </ul>
 */
@FunctionalInterface
public interface PayoutNoFactory {

    /**
     * 生成单号。
     *
     * @param withdrawalNo 提现单号（仓储在锁内取出后传入，Service 不碰）
     * @param attemptNo    尝试序号（锁内由 {@code max(attempt_no) + 1} 得出）
     * @return 出款单号
     */
    String create(String withdrawalNo, int attemptNo);
}
