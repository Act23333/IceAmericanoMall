package org.icedamericanomall.user.app.oplog;

/**
 * user 域管理操作审计常量（ADR-032 管道二）。
 *
 * <p>{@code operation_log} 表<b>没有</b> module 列，写侧把模块限定词并入 {@code action}
 * （{@code MODULE:ACTION}），既保留模块语义，又让 {@code AdminLogController} 的
 * {@code action} 字段自解释。集中在此处可保证同一动作在台账中的取值稳定。
 *
 * <p><b>{@code target} 列的格式（U17 定死，2026-10-18）</b>：存<b>纯业务键字符串</b>
 * （如用户技术主键 {@code "7"}）—— 不带 {@code userId=} 之类键名前缀、不带实体类型前缀。
 * 理由：前缀会逼每个消费者各自 {@code split}，而列名已经声明了语义；将来多实体共表时
 * <b>加列</b>区分，不要在 {@code target} 里塞复合编码（那是「字段里复写会漂的结构」的复发形态）。
 * <p><b>补偿闭环只允许按 {@code action} + {@code target} + {@code create_time} 判定</b>，
 * <b>禁止解析 {@code detail}</b> —— 那是人读文案，随时会改。
 */
public final class OperationAuditConstants {

    /** 模块与动作的分隔符（{@code MODULE:ACTION}）—— 写入与查询必须共用，故集中在常量类里。 */
    public static final String MODULE_ACTION_SEPARATOR = ":";

    /** 模块：user 档案域管理操作。 */
    public static final String MODULE_USER = "USER";

    /** 动作：管理后台启用/禁用用户（<b>启用</b>走这个；禁用走下面的独立 action）。 */
    public static final String ACTION_UPDATE_USER_STATUS = "UPDATE_USER_STATUS";

    /**
     * 动作：管理后台<b>禁用</b>用户（独立 action）。
     *
     * <p>为什么要单列：令牌吊销的对账<b>只按 action 判定</b>候选集 —— 若沿用
     * {@code UPDATE_USER_STATUS}，就只能从 {@code detail} 里的 {@code status=FROZEN} 文案判断，
     * 那正是要禁止的解析。单列后「有禁用记录、无吊销终结记录」就是一个干净的列条件。
     */
    public static final String ACTION_DISABLE_USER = "DISABLE_USER";

    /**
     * 动作：用户级令牌吊销<b>已终结</b>（写入即表示该用户这一轮吊销不再需要重试）。
     *
     * <p>三种终结理由（写在 {@code detail}，<b>仅供人读、不参与判定</b>）：
     * {@code ok}（成功）/ {@code permanent-4xx}（确定性失败，重试必然重复失败）/
     * {@code skipped: re-enabled or missing}（对账时发现已重新启用或用户已不存在 ——
     * <b>绝不能踢掉重新启用的正常用户</b>）。
     */
    public static final String ACTION_REVOKE_USER_TOKENS = "REVOKE_USER_TOKENS";

    /** 动作：管理后台修改用户角色（委托 authorization-service 执行）。 */
    public static final String ACTION_SET_USER_ROLE = "SET_USER_ROLE";

    private OperationAuditConstants() {
    }

    /** 组装台账里 {@code action} 列的取值（唯一入口，避免各处手拼分隔符）。 */
    public static String actionKey(String module, String action) {
        return module + MODULE_ACTION_SEPARATOR + action;
    }
}
