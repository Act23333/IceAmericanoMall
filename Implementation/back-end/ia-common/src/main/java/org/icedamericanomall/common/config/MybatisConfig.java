package org.icedamericanomall.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 拦截器装配（内核共享）。
 *
 * <p>内含两个 InnerInterceptor，<b>顺序不可调换</b>（官方推荐：分页 → 乐观锁）：
 * <ol>
 *   <li>{@link PaginationInnerInterceptor} —— 分页，单页上限 {@value #MAX_PAGE_SIZE} 条，防止
 *       {@code size=999999} 之类的深分页拖垮数据库；</li>
 *   <li>{@link OptimisticLockerInnerInterceptor} —— 乐观锁：实体带
 *       {@code @Version} 且版本值非空时，自动把 {@code updateById/update} 改写成
 *       {@code SET version = version + 1 ... WHERE ... AND version = ?}。
 *       <p><b>V5.3 补齐（P2 缺陷）</b>：此前全仓库没有注册该拦截器，{@code orders}/{@code sku}
 *       等实体上的 {@code @Version} 注解<b>完全不生效</b>（注解被静默忽略），
 *       并发更新会互相覆盖。trade-service 侧虽已改为条件更新兜底，但 {@code @Version} 的
 *       语义必须真实生效，否则其他服务的并发路径处于无保护状态。</li>
 * </ol>
 *
 * <p><b>注意</b>：乐观锁只在版本值非空时参与条件；实体版本为 {@code null} 时行为与此前一致
 * （不加版本条件），因此存量「不查版本直接 update」的代码不会因此突然失败。
 */
@Configuration
@ConditionalOnClass(MybatisConfiguration.class)
public class MybatisConfig {

    /** 单页最大条数上限。 */
    private static final long MAX_PAGE_SIZE = 1000L;

    @Bean
    @ConditionalOnMissingBean //当 Spring 容器中还没有 MybatisPlusInterceptor 类型的 Bean 时，才创建这个 Bean，此时要求既没有指定类型的 Bean，也没有指定名称的 Bean 时才创建。
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        //分页拦截器
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        //乐观锁拦截器（让 @Version 真正生效；必须在分页之后加入）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
