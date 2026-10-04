package org.icedamericanomall.user.app.oplog;

/**
 * user 域管理操作审计常量（ADR-032 管道二）。
 *
 * <p>{@code operation_log} 表<b>没有</b> module 列，写侧把模块限定词并入 {@code action}
 * （{@code MODULE:ACTION}），既保留模块语义，又让 {@code AdminLogController} 的
 * {@code action} 字段自解释。集中在此处可保证同一动作在台账中的取值稳定。
 */
public final class OperationAuditConstants {

    /** 模块：user 档案域管理操作。 */
    public static final String MODULE_USER = "USER";

    /** 动作：管理后台启用/禁用用户。 */
    public static final String ACTION_UPDATE_USER_STATUS = "UPDATE_USER_STATUS";

    /** 动作：管理后台修改用户角色（委托 authorization-service 执行）。 */
    public static final String ACTION_SET_USER_ROLE = "SET_USER_ROLE";

    private OperationAuditConstants() {
    }
}
