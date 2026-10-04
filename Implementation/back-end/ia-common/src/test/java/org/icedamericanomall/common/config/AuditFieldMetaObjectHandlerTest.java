package org.icedamericanomall.common.config;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link AuditFieldMetaObjectHandler} 填充语义单测（V5.5 归并后锁定行为）。
 *
 * <p>MyBatis-Plus 的 <b>严格填充</b>依赖 {@code TableInfo} 缓存（{@code strictInsertFill} 会先
 * {@code findTableInfo(metaObject)}，只有实体字段标注了 {@code @TableField(fill = ...)} 才真正写入），
 * 因此本用例先用 {@link TableInfoHelper} 为测试实体建表信息，再驱动填充器 —— 这是不启 Spring 容器
 * 也能验证「填了哪几个字段」「不填哪几个字段」的最小路径。
 */
@DisplayName("审计字段填充器 —— 严格填充语义")
class AuditFieldMetaObjectHandlerTest {

    private final AuditFieldMetaObjectHandler handler = new AuditFieldMetaObjectHandler();

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AuditDemoEntity.class);
        TableInfoHelper.initTableInfo(assistant, NoFillEntity.class);
    }

    @Test
    @DisplayName("插入填充 — 同一次插入的 createTime 与 updateTime 相等，且业务字段不被填充")
    void shouldFillCreateAndUpdateTimeOnInsert_whenFieldsAreNull() {
        AuditDemoEntity entity = new AuditDemoEntity();

        handler.insertFill(metaObjectOf(entity));

        assertNotNull(entity.createTime, "createTime 应被插入填充");
        assertNotNull(entity.updateTime, "updateTime 应被插入填充");
        assertEquals(entity.createTime, entity.updateTime,
                "同一次插入的两个时间戳必须取自同一个 now（归并前的副本语义）");
        assertNull(entity.registerTime,
                "通用填充器不得触碰业务专属字段（registerTime 由 user-service 自有填充器负责）");
    }

    @Test
    @DisplayName("插入填充 — 已有值不被覆盖（严格模式 null 才填）")
    void shouldNotOverwriteOnInsert_whenFieldAlreadyHasValue() {
        LocalDateTime preset = LocalDateTime.of(2020, 1, 1, 0, 0, 0);
        AuditDemoEntity entity = new AuditDemoEntity();
        entity.createTime = preset;

        handler.insertFill(metaObjectOf(entity));

        assertEquals(preset, entity.createTime, "已有 createTime 不应被覆盖");
        assertNotNull(entity.updateTime, "为空的 updateTime 应被填充");
    }

    @Test
    @DisplayName("更新填充 — 只填 updateTime，createTime 保持不变")
    void shouldFillOnlyUpdateTimeOnUpdate() {
        LocalDateTime preset = LocalDateTime.of(2020, 1, 1, 0, 0, 0);
        AuditDemoEntity entity = new AuditDemoEntity();
        entity.createTime = preset;

        handler.updateFill(metaObjectOf(entity));

        assertEquals(preset, entity.createTime, "更新填充不得改写 createTime");
        assertNotNull(entity.updateTime, "updateTime 应被更新填充");
    }

    @Test
    @DisplayName("更新填充 — updateTime 已有值时不覆盖")
    void shouldNotOverwriteUpdateTime_whenAlreadySet() {
        LocalDateTime preset = LocalDateTime.of(2021, 6, 6, 6, 6, 6);
        AuditDemoEntity entity = new AuditDemoEntity();
        entity.updateTime = preset;

        handler.updateFill(metaObjectOf(entity));

        assertEquals(preset, entity.updateTime, "已有 updateTime 不应被覆盖");
    }

    @Test
    @DisplayName("严格模式 — 未标注 fill 的字段一律不填充")
    void shouldNotFill_whenEntityHasNoFillAnnotations() {
        NoFillEntity entity = new NoFillEntity();

        handler.insertFill(metaObjectOf(entity));
        handler.updateFill(metaObjectOf(entity));

        assertNull(entity.createTime, "无 @TableField(fill) 注解的实体不应被填充");
        assertNull(entity.updateTime, "无 @TableField(fill) 注解的实体不应被填充");
    }

    private static MetaObject metaObjectOf(Object target) {
        return SystemMetaObject.forObject(target);
    }

    /** 带审计字段（+一个业务专属字段）的演示实体。 */
    @TableName("audit_demo")
    static class AuditDemoEntity {

        @TableId(type = IdType.AUTO)
        private Long id;

        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime createTime;

        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updateTime;

        /** 模拟 user-service 的 {@code register_time}：通用填充器不应识别它。 */
        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime registerTime;
    }

    /** 无任何填充注解的实体（严格模式应完全不生效）。 */
    @TableName("no_fill_demo")
    static class NoFillEntity {

        @TableId(type = IdType.AUTO)
        private Long id;

        private LocalDateTime createTime;

        private LocalDateTime updateTime;
    }
}
