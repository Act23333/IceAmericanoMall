package org.icedamericanomall.fallback.fixture;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * <b>守卫变异样本（故意违规，永不进入生产构建，也永不进入 ia-api jar）</b> ——
 * {@code FallbackCategoryGuardTest} 用它证明「类别判定唯一入口」这条 ArchUnit 规则真的会咬人，
 * 而不是一条永远为真的空规则。
 *
 * <p>违规形态：本类<b>会抛</b> {@link BizException}（即「会抛错的降级工厂」），
 * 却<b>完全不引用</b> {@code org.icedamericanomall.exception.UpstreamFailures} ——
 * 它自己写了一份「这是不是一条客户端错误」的判断。这正是 09-constraints §三·八 立条要消灭的
 * 「解码器一套、降级一套」：同一枚故障被两个地方分别判定，可能得出两个结论。
 *
 * <p><b>为什么本文件放在 {@code ia-api/src/test/java}（而不是 architecture-tests）</b>：
 * <ul>
 *   <li>{@code architecture-tests} 刻意<b>不依赖任何业务模块</b>（该模块 pom §description），
 *       它没有 ia-common 的编译期类路径 ⇒ 本类放在那里根本编译不过；</li>
 *   <li>早期实现是「放进 architecture-tests 的 {@code src/test/resources}，测试时用
 *       {@code javax.tools.JavaCompiler} 现场编译到临时目录」。**这条路径被证明是 flaky 的**：
 *       单跑绿、整模块跑红（变异样本未被导入 ⇒ 反空断言报「变异样本未被规则选中」），
 *       而 2026-10-18 那次全量绿只是运气。一个 flaky 的守卫迟早在被 {@code @Disabled} 中死掉；</li>
 *   <li>放到 ia-api 的测试源后，它就是**普通的编译产物**（{@code ia-api/target/test-classes}），
 *       architecture-tests 用与其他守卫完全一致的 {@code importPaths} 方式导入 ——
 *       确定性、无运行期编译器、无临时目录。代价是 architecture-tests 依赖「ia-api 的测试类已编译」
 *       这一前置（reactor 顺序天然保证；单独跑该模块缺失时会给出可执行的提示）。</li>
 * </ul>
 *
 * <p>包名 {@code org.icedamericanomall.fallback.fixture} 是<b>硬要求</b>：必须落在守卫的扫描包
 * {@code org.icedamericanomall.fallback..} 之内，否则规则选不中它，变异验证会变成空验证。
 * 又因为它在 {@code src/test} 下，{@code FallbackDegradeContractTest} 扫描的
 * {@code ia-api/target/classes}（main）永远不会发现它 —— 不会污染真实工厂清单。
 */
public class BypassFallbackFixture {

    /**
     * 降级工厂的入口形态（签名与真实工厂一致，便于人读；规则本身只看依赖画像）。
     *
     * @param cause Feign 失败原因
     * @return 本样本永远抛错，不会返回
     */
    public Object create(Throwable cause) {
        // ❌ 违规：自己判类别（真实工厂此处必须 UpstreamFailures.classify(cause)）
        boolean selfJudgedClientError = cause instanceof IllegalArgumentException;
        if (selfJudgedClientError) {
            throw new IllegalStateException("自写一份「是不是 CLIENT」的判断（变异样本）");
        }
        // ❌ 违规：把一切都改写成 SYSTEM_BUSY，且不依赖 UpstreamFailures
        throw new BizException(ErrorCode.SYSTEM_BUSY, "绕过 UpstreamFailures 自行判定类别（变异样本）");
    }
}
