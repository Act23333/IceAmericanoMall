package org.icedamericanomall.fallback.fixture;

import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

/**
 * <b>守卫变异样本（故意违规，永不进入生产构建）</b> ——
 * {@code FallbackCategoryGuardTest} 用它证明「类别判定唯一入口」这条 ArchUnit 规则真的会咬人，
 * 而不是一条永远为真的空规则。
 *
 * <p>违规形态：本类<b>会抛</b> {@link BizException}（即「会抛错的降级工厂」），
 * 却<b>完全不引用</b> {@code org.icedamericanomall.exception.UpstreamFailures} ——
 * 它自己写了一份「这是不是一条客户端错误」的判断。这正是 09-constraints §三·八 立条要消灭的
 * 「解码器一套、降级一套」：同一枚故障被两个地方分别判定，可能得出两个结论。
 *
 * <p><b>为什么本文件放在 {@code src/test/resources} 而不是 {@code src/test/java}</b>：
 * {@code architecture-tests} 模块刻意<b>不依赖任何业务模块</b>（见该模块 pom 的说明），
 * 因此 {@code org.icedamericanomall.common.exception.BizException}（ia-common）不在它的
 * <b>编译期</b>类路径上 —— 放在 {@code src/test/java} 会直接编译失败。守卫测试改为在运行时用
 * {@code javax.tools.JavaCompiler} 以 {@code ../ia-common/target/classes} 为 classpath 现场编译
 * 本文件（编译产物落临时目录），再用 ArchUnit 导入该目录。这样既能得到「真实编译出的 class 文件」，
 * 又不必为了一个测试样本去改模块的依赖声明。
 *
 * <p>包名 {@code org.icedamericanomall.fallback.fixture} 是<b>硬要求</b>：必须落在守卫的扫描包
 * {@code org.icedamericanomall.fallback..} 之内，否则规则选不中它，变异验证会变成空验证。
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
