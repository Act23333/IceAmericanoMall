package org.icedamericanomall.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 审计字段填充器（ia-common 共享内核的唯一实现，替代此前各服务私有的
 * {@code MyMetaObjectHandler} 副本）。
 *
 * <p><b>填充语义（与历史副本逐字段对齐，勿轻改）</b>：
 * <ul>
 *   <li>{@link #insertFill(MetaObject)} —— 同一次插入使用<b>同一个</b>
 *       {@code LocalDateTime now} 填 {@code createTime} 与 {@code updateTime}
 *       （保证新建行两个时间戳严格相等）；</li>
 *   <li>{@link #updateFill(MetaObject)} —— 只填 {@code updateTime}。</li>
 * </ul>
 * 均使用 MyBatis-Plus 的 {@code strictXxxFill} 严格模式：<b>只有</b>实体字段标注了
 * {@code @TableField(fill = FieldFill.INSERT / INSERT_UPDATE)} 且为 {@code LocalDateTime}
 * 类型、当前值为 null 时才会写入 —— 因此对没有审计字段的实体完全无副作用。
 *
 * <p><b>为什么不做「用户自定义字段」</b>：user-service 的 {@code user} 表额外有
 * {@code register_time}（注册时间，仅插入填充），故 user-service 保留了自己的
 * {@code MyMetaObjectHandler} 副本；{@link MyMetaObjectHandlerAutoConfiguration} 通过
 * {@code @ConditionalOnMissingBean(MetaObjectHandler.class)} 自动退避，两者不会冲突。
 * 若把 {@code registerTime} 也塞进本类，会让「通用审计字段」耦合到单个业务表，
 * 违背共享内核的职责边界。
 */
public class AuditFieldMetaObjectHandler implements MetaObjectHandler {

    /** 创建时间字段名（对应 PO 的 {@code createTime} 属性）。 */
    private static final String FIELD_CREATE_TIME = "createTime";

    /** 更新时间字段名（对应 PO 的 {@code updateTime} 属性）。 */
    private static final String FIELD_UPDATE_TIME = "updateTime";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, FIELD_CREATE_TIME, LocalDateTime.class, now);
        this.strictInsertFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
    }
}
