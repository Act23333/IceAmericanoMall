package org.icedamericanomall.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构规则守卫（ArchUnit）—— 把「只写在文档里、靠人记」的分层与边界规则变成构建期机械检查
 * （2026-10-10 CR 结论落地；与 {@code scripts/check-id-invariants.sh} 互补：
 * 脚本管文本级约定，本模块管语义级依赖）。
 *
 * <p>扫描方式：逐模块 importPaths（{@code ../<module>/target/classes}），不依赖业务模块
 * 的 Spring Boot fat-jar 形态；构建顺序由父 POM modules 声明序保证（本模块置末）。
 * 单模块运行前需先完成全量编译（见 {@link #importAll()} 的报错提示）。
 */
@DisplayName("架构守卫 — 四层依赖 / PO 边界 / adapter 边界 / ID 生成出口")
class ArchitectureTest {

    /** 参与扫描的模块（相对本模块目录）；新增模块时同步维护。 */
    private static final List<String> MODULES = List.of(
            "ia-common", "ia-api", "ia-integration",
            "user-service", "authorization-service", "gateway-service",
            "cart-service", "pay-service", "trade-service", "item-service",
            "logistics-service", "search-service", "ai-service", "marketing-service",
            "notification-service", "verification-service", "wallet-service", "merchant-service");

    /** UUID/IdUtil 生成白名单（非业务标识用途；业务 ID 一律 IdGenerator，ADR-050）。 */
    private static final Set<String> UUID_GENERATION_ALLOWED = Set.of(
            "org.icedamericanomall.common.filter.TraceIdFilter",
            "org.icedamericanomall.common.filter.UserContextFilter",
            "org.icedamericanomall.common.event.DomainEvent",
            "org.icedamericanomall.gateway.filter.TraceIdFilter",
            "org.icedamericanomall.integration.storage.MockStorageClient",
            "org.icedamericanomall.trade.infrastructure.adapter.MockInvoiceIssueAdapter");

    private static JavaClasses classes;

    @BeforeAll
    static void importAll() {
        List<Path> paths = MODULES.stream()
                .map(module -> Paths.get("..", module, "target", "classes"))
                .peek(path -> {
                    if (!Files.isDirectory(path)) {
                        throw new IllegalStateException("缺少编译产物: " + path.toAbsolutePath()
                                + " —— 请先在 back-end 根目录执行 mvn -DskipTests install（或从根运行全量 mvn test 由 reactor 保证顺序）");
                    }
                })
                .toList();
        classes = new ClassFileImporter().importPaths(paths);
    }

    @Test
    @DisplayName("四层依赖方向 — domain 不得依赖框架（spring/mybatis/ibatis）与 infra/adapter/app 层（ADR-038）")
    void domainDependsOnNothingOutward() {
        // 存量基线债务（2026-10-10 冻结，见 archunit_store/ 与 11-Test-Strategy §〇.1）：
        //   ① 22 处 domain 仓储端口以 MyBatis-Plus IPage 作返回类型——偿还方向：域层 PageResult 抽象；
        //   ② 3 处 trade 域枚举携带 @EnumValue——偿还方向：PO Integer 映射（user-service 已是此模式）。
        // FreezingArchRule：存量违规不阻断构建，任何【新增】违规立即失败（基线只减不增）。
        ArchRule rule = FreezingArchRule.freeze(noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "com.baomidou..", "org.apache.ibatis..",
                        "..infrastructure..", "..adapter..", "..app..")
                .because("domain 层按 ADR-038 必须框架无关（ia-common 共享内核 / lombok / JDK 允许；存量基线已冻结）"));
        rule.check(classes);
    }

    @Test
    @DisplayName("PO 边界 — persistence 之外禁止直接依赖 *PO（ADR-003/038 对象隔离）")
    void poNeverLeaksOutsidePersistence() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..infrastructure..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("PO")
                .because("PO 是持久化内部模型，外部只见 DTO/VO/聚合；PO 出 persistence 即评审拒绝项");
        rule.check(classes);
    }

    @Test
    @DisplayName("Adapter 边界 — adapter 层禁止依赖 infrastructure.persistence（Controller 不得见 Mapper/PO）")
    void adapterMustNotTouchPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..adapter..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure.persistence..")
                .because("Controller 只做参数校验 + Manager 调用（ADR-023/038）；Controller→Mapper 是 AGENTS 反模式");
        rule.check(classes);
    }

    @Test
    @DisplayName("ID 生成出口 — UUID.randomUUID / IdUtil.fastSimpleUUID 仅限白名单场景（ADR-050）")
    void businessIdGenerationIsCentralized() {
        ArchRule rule = noClasses()
                .that(describe("不在 UUID 生成白名单内", javaClass -> !UUID_GENERATION_ALLOWED.contains(javaClass.getName())))
                .should().callCodeUnitWhere(
                        target(owner(name("java.util.UUID")).and(name("randomUUID")))
                                .or(target(owner(name("cn.hutool.core.util.IdUtil")).and(name("fastSimpleUUID")))))
                .because("业务 ID 一律 IdGenerator.nextId()/secureToken()（ADR-050）；白名单为 trace/event/mock 非业务场景");
        rule.check(classes);
    }
}
