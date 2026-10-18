package org.icedamericanomall.user.app.oplog;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.security.UserInfo;
import org.icedamericanomall.user.adapter.response.OperationLogVO;
import org.icedamericanomall.user.domain.model.oplog.OperationLog;
import org.icedamericanomall.user.domain.repository.OperationLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 操作日志读写（应用层）。
 *
 * <p><b>读</b>：管理后台分页查询 {@code operation_log} 表（{@code AdminLogController}）。
 *
 * <p><b>写（ADR-032 管道二）</b>：user 域自身的<b>管理操作</b>在成功路径上显式调用
 * {@link #record(Long, String, String, String, String)} 落库 —— 保住「管理端可查询的审计台账」这一既有能力。
 * 刻意<b>不经 AOP 直写数据库</b>：一旦切面持有 Mapper，审计能力就会把共享内核反向耦合到各服务的
 * 持久化实现上（见 ADR-032 决策）。全平台其余服务的管理操作走管道一
 * （{@code @OperationLog} → ELK JSON 审计日志），<b>不落此表</b>。
 *
 * <p><b>分层整改（2026-10-10 架构守卫）</b>：本类不再触碰 Mapper/PO——读写经
 * {@link OperationLogRepository} 领域端口，PO ↔ 领域模型转换下沉到基础设施层
 * （原实现直操 PO 且 {@code OperationLogVO.from(PO)}，违反 ADR-003/038 对象隔离）。
 *
 * <p><b>列映射</b>：{@code userId} → {@code user_id}；{@code module + ":" + action} → {@code action}
 * （表无 module 列，故模块限定词并入 action）；{@code content} → {@code detail}；
 * {@code username} / {@code ip} 取当前请求的 {@link UserContext}（取不到则留空）。
 * {@code create_time} 交由列默认值 {@code CURRENT_TIMESTAMP} 填充。
 * 调用方需保证 {@code action} ≤ 50 字符、{@code content} ≤ 500 字符（列长上限），超长在严格模式下写入失败。
 */
@Component
@RequiredArgsConstructor
public class OperationLogManager {

    private final OperationLogRepository operationLogRepository;

    /** 管理后台：分页查询操作日志（按时间倒序）。 */
    public IPage<OperationLogVO> pageLogs(int pageNum, int size) {
        int offset = Math.max(0, (pageNum - 1) * size);
        long total = operationLogRepository.countAll();
        Page<OperationLogVO> page = new Page<>(pageNum, size);
        page.setTotal(total);
        page.setRecords(operationLogRepository.findPageDesc(offset, size).stream()
                .map(OperationLogVO::from)
                .toList());
        return page;
    }

    /**
     * 记录一条管理操作审计（user 域落库，ADR-032 管道二）。
     *
     * <p>与调用方的本地状态变更共处同一事务（调用方带 {@code @Transactional} 时传播复用），
     * 保证「本地变更成功 ⟺ 审计留痕」；若在调用方事务之外（如含 Feign 的角色授予），
     * 本方法自成一个独立事务，审计失败不回滚已完成的中台变更。
     *
     * @param userId  操作人技术主键（用户维度检索用），可为 {@code null}（系统/匿名调用）
     * @param module  业务模块标识（如 {@code USER}），建议取常量
     * @param action  操作动作标识（如 {@code UPDATE_USER_STATUS}），建议取常量
     * @param target  操作目标（<b>纯业务键字符串</b>，如目标用户技术主键 {@code "7"}）——
     *                补偿对账按它检索；<b>不要</b>带键名前缀或塞复合编码，也不要放敏感值
     * @param content 操作摘要（动作 + 目标标识等非敏感信息），不得写入手机号/密文等敏感值
     * @throws org.springframework.dao.DataAccessException 审计写入失败（是否回滚由调用方事务语义决定）
     */
    @Transactional(rollbackFor = Exception.class)
    public void record(Long userId, String module, String action, String target, String content) {
        UserInfo operator = UserContext.getUser();
        operationLogRepository.save(OperationLog.builder()
                .userId(userId)
                .username(operator == null ? null : operator.username())
                .action(OperationAuditConstants.actionKey(module, action))
                .target(target)
                .detail(content)
                .ip(operator == null ? null : operator.clientIp())
                .build());
    }
}
