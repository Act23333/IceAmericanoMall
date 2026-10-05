package org.icedamericanomall.marketing.app.task;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.marketing.adapter.request.TaskCreateReq;
import org.icedamericanomall.marketing.adapter.request.TaskUpdateReq;
import org.icedamericanomall.marketing.adapter.response.TaskDefVO;
import org.icedamericanomall.marketing.domain.model.task.TaskDef;
import org.icedamericanomall.marketing.domain.repository.TaskDefRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务定义管理端用例族编排（应用层 · 平台后台，V5.13 AD51-AD53，任务运营闭环）。
 *
 * <p>与 {@link TaskManager}（I49 完成发放 / M14 任务中心）按用例族拆分（ADR-025，
 * 镜像 {@code PromotionAdminManager} 先例）。两个写用例均为纯本地 DB 操作，
 * 事务边界在本层（{@code rollbackFor} 全量回滚）。
 *
 * <p><b>运营口径</b>：{@code rewardPoints} 调整立即生效——I49 完成发放按完成时刻的
 * 定义快照入账，不追溯已发流水；{@code status=0} 停用后触发口 I49 经
 * {@code TaskDef.isEnabled()} 守卫立即回落 {@code completed=false}。
 */
@Component
@RequiredArgsConstructor
public class TaskAdminManager {

    private final TaskDefRepository taskDefRepository;

    /**
     * AD51：分页查询任务定义（status 过滤可选，code 升序稳定排序）。
     *
     * @param status   状态过滤（null=全部）
     * @param pageNum  页码（1 起，缺省 1 由适配层归一）
     * @param pageSize 页大小（缺省 20 由适配层归一）
     * @return 任务定义分页（管理端视图，不含技术主键）
     */
    public IPage<TaskDefVO> page(Integer status, int pageNum, int pageSize) {
        return taskDefRepository.page(status, pageNum, pageSize).convert(this::toVO);
    }

    /**
     * AD52：创建任务定义（code 业务唯一）。
     *
     * <p>唯一性采用「先查后插 + {@code uk_code} 唯一键兜底」双路径，均统一回落
     * {@code PARAM_ERROR(1101)} / 400；type/status 缺省归一（1=每日启用），
     * Phase 1 值域由聚合不变式守护。
     *
     * @param req 创建请求（字段必填与正则校验已在适配层完成）
     * @throws BizException code 重复（先查命中或并发撞唯一键，
     *                      {@link ErrorCode#PARAM_ERROR}，400）；
     *                      类型非每日任务（聚合守卫，{@link ErrorCode#PARAM_ERROR}，400）
     */
    @Transactional(rollbackFor = Exception.class)
    public void create(TaskCreateReq req) {
        ensureCodeAvailable(req.getCode());
        TaskDef taskDef = TaskDef.builder()
                .code(req.getCode())
                .name(req.getName())
                .type(req.getType() == null ? TaskDef.TYPE_DAILY : req.getType())
                .rewardPoints(req.getRewardPoints())
                .status(req.getStatus() == null ? TaskDef.STATUS_ENABLED : req.getStatus())
                .build();
        taskDef.ensureTypeSupported();
        try {
            taskDefRepository.save(taskDef);
        } catch (DuplicateKeyException e) {
            // 先查后插的竞态兜底（uk_code 唯一键）：并发创建同一 code → 统一 400
            throw new BizException(ErrorCode.PARAM_ERROR, "任务编码已存在: " + taskDef.getCode());
        }
    }

    /**
     * AD53：编辑任务定义（null-safe 局部更新；code 仅作寻址键不可改）。
     *
     * @param code 任务编码（业务唯一键，路径寻址）
     * @param req  编辑请求（name/rewardPoints/status 任传任改）
     * @throws BizException 任务不存在（{@link ErrorCode#NO_RESOURCE_FOUND}，404）
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(String code, TaskUpdateReq req) {
        TaskDef taskDef = requireByCode(code);
        if (req.getName() != null) {
            taskDef.setName(req.getName());
        }
        if (req.getRewardPoints() != null) {
            taskDef.setRewardPoints(req.getRewardPoints());
        }
        if (req.getStatus() != null) {
            taskDef.setStatus(req.getStatus());
        }
        taskDefRepository.update(taskDef);
    }

    // ==================== 内部方法 ====================

    /** code 唯一性先查（主路径）；并发竞态由 uk_code 唯一键兜底。 */
    private void ensureCodeAvailable(String code) {
        if (taskDefRepository.findByCode(code) != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "任务编码已存在: " + code);
        }
    }

    /** 按业务唯一键取定义，不存在抛 NO_RESOURCE_FOUND（AD53 契约错误口径 1103）。 */
    private TaskDef requireByCode(String code) {
        TaskDef taskDef = taskDefRepository.findByCode(code);
        if (taskDef == null) {
            throw new BizException(ErrorCode.NO_RESOURCE_FOUND, "任务定义不存在: " + code);
        }
        return taskDef;
    }

    /** 任务定义聚合 → 管理端视图（不透出技术主键 id）。 */
    private TaskDefVO toVO(TaskDef taskDef) {
        TaskDefVO vo = new TaskDefVO();
        vo.setCode(taskDef.getCode());
        vo.setName(taskDef.getName());
        vo.setType(taskDef.getType());
        vo.setRewardPoints(taskDef.getRewardPoints());
        vo.setStatus(taskDef.getStatus());
        vo.setCreateTime(taskDef.getCreateTime());
        return vo;
    }
}
