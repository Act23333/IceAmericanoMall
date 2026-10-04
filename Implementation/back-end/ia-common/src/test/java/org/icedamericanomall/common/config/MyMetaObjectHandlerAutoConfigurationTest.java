package org.icedamericanomall.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MyMetaObjectHandlerAutoConfiguration} 装配行为测试（V5.5 归并）。
 *
 * <p>锁定两条关键契约：
 * <ol>
 *   <li>无自定义填充器时，自动装配出唯一的 {@link AuditFieldMetaObjectHandler}；</li>
 *   <li>存在自定义填充器（如 user-service 那份多填 {@code registerTime} 的）时，
 *       自动配置<b>整体退避</b>，容器内仍只有一个 {@code MetaObjectHandler}，且是用户那份 ——
 *       这正是 8 份副本能收敛成「1 份共享 + 1 份 user 自有」而互不冲突的机制。</li>
 * </ol>
 */
@DisplayName("审计字段填充器自动装配 —— 缺省装配与用户 Bean 退避")
class MyMetaObjectHandlerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MyMetaObjectHandlerAutoConfiguration.class));

    @Test
    @DisplayName("无自定义填充器 → 装配通用填充器（唯一 Bean）")
    void shouldRegisterCommonHandler_whenNoCustomHandler() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MetaObjectHandler.class);
            assertThat(context.getBean(MetaObjectHandler.class))
                    .isInstanceOf(AuditFieldMetaObjectHandler.class);
        });
    }

    @Test
    @DisplayName("存在自定义填充器 → 自动配置退避，保留用户 Bean")
    void shouldBackOff_whenCustomHandlerPresent() {
        MetaObjectHandler userOwned = new NoopMetaObjectHandler();

        runner.withBean("userOwnedMetaObjectHandler", MetaObjectHandler.class, () -> userOwned)
                .run(context -> {
                    assertThat(context.getBeanNamesForType(MetaObjectHandler.class)).hasSize(1);
                    assertThat(context.getBean(MetaObjectHandler.class)).isSameAs(userOwned);
                });
    }

    /** 用户侧自定义填充器替身（模拟 user-service 的 {@code MyMetaObjectHandler}）。 */
    private static class NoopMetaObjectHandler implements MetaObjectHandler {

        @Override
        public void insertFill(MetaObject metaObject) {
            // 无操作
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            // 无操作
        }
    }
}
