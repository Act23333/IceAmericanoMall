package org.icedamericanomall.item.adapter.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.annotation.OperationLog;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.dto.KgNeighborCO;
import org.icedamericanomall.item.adapter.request.PairingRuleReq;
import org.icedamericanomall.item.adapter.request.PairingRuleUpdateReq;
import org.icedamericanomall.item.adapter.response.PairingRuleVO;
import org.icedamericanomall.item.app.kg.KgAdminManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台后台知识图谱治理接口（F15 AD61-AD65，契约 §4.13.8f；D6-A 最小治理面，
 * O5 裁决落位 item 管理端；网关 {@code /api/admin/kg/**} 须先于 {@code /api/admin/**}
 * 兜底声明——ADR-035/036 先例）。
 *
 * <p>控制器只做参数校验与委托：规则分页 / 创建（类目存在性 + 唯一键防重）/
 * 编辑（null-safe，启停即时生效）/ 全量重建（US-KG-8 兜底轨）/ 关系邻居核查查询
 * （AD65，US-KG-5 表格化视图）编排下沉到 {@link KgAdminManager}。
 *
 * <p><b>审计（ADR-032 管道一，US-KG-7）</b>：全部写操作标注 {@code @OperationLog}，
 * 产出单行 JSON 审计日志（logger {@code AUDIT}）；默认不记参数值（PII 安全默认）；
 * AD65 为只读查询，不标注。
 */
@RestController
// 权限位拆分（5a，2026-10-18）：知识图谱与配对规则治理（平台级配置）
@PreAuthorize("@ss.hasPermi('kg:admin')")
@RequestMapping("/api/admin/kg")
@RequiredArgsConstructor
public class AdminKgController {

    /** 审计模块：商品域知识图谱治理。 */
    private static final String MODULE_ITEM_KG = "ITEM_KG";

    /** 审计动作：创建搭配规则。 */
    private static final String ACTION_CREATE_RULE = "CREATE_PAIRING_RULE";

    /** 审计动作：编辑搭配规则（含启停）。 */
    private static final String ACTION_UPDATE_RULE = "UPDATE_PAIRING_RULE";

    /** 审计动作：全量重建图边投影。 */
    private static final String ACTION_REBUILD_EDGES = "REBUILD_KG_EDGES";

    private final KgAdminManager kgAdminManager;

    /** AD61：分页查询搭配规则（src/dst/status 过滤可选；page 缺省 1，size 缺省 20）。 */
    @GetMapping("/rule/page")
    public Result<IPage<PairingRuleVO>> page(@RequestParam(name = "src_category_id", required = false) Long srcCategoryId,
                                             @RequestParam(name = "dst_category_id", required = false) Long dstCategoryId,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(kgAdminManager.page(srcCategoryId, dstCategoryId, status, page, size));
    }

    /** AD62：创建搭配规则（类目悬空 → 1101；重复 → 400；落库即同步边投影）。 */
    @OperationLog(module = MODULE_ITEM_KG, action = ACTION_CREATE_RULE, description = "创建类目搭配规则")
    @PostMapping("/rule")
    public Result<Void> create(@Valid @RequestBody PairingRuleReq req) {
        kgAdminManager.create(req);
        return Result.ok();
    }

    /** AD63：编辑搭配规则（null-safe 局部更新；不存在 → 1103；启停即时生效）。 */
    @OperationLog(module = MODULE_ITEM_KG, action = ACTION_UPDATE_RULE, description = "编辑类目搭配规则")
    @PutMapping("/rule/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody PairingRuleUpdateReq req) {
        kgAdminManager.update(id, req);
        return Result.ok();
    }

    /** AD64：全量重建边投影（按真相源逐 src 幂等覆盖；返回重建边数，可重试）。 */
    @OperationLog(module = MODULE_ITEM_KG, action = ACTION_REBUILD_EDGES, description = "全量重建图边投影")
    @PostMapping("/rebuild")
    public Result<Integer> rebuild() {
        return Result.ok(kgAdminManager.rebuild());
    }

    /**
     * AD65：关系邻居表格化核查视图（US-KG-5，V5.16.1）——按 src 查询邻居列表
     * （关系类型/目标/权重/评分），包装 I58 检索面；ES 降级 → 空列表（fail-open 由
     * ia-api fallback 承担）。srcType/srcId 必填与 depth 边界（缺省 1，≤2）校验在
     * {@link KgAdminManager#neighbors}（缺失/超界 → 1101/400）。
     */
    @GetMapping("/neighbors")
    public Result<List<KgNeighborCO>> neighbors(@RequestParam(name = "src_type", required = false) String srcType,
                                                @RequestParam(name = "src_id", required = false) Long srcId,
                                                @RequestParam(name = "rel_type", required = false) String relType,
                                                @RequestParam(name = "dst_type", required = false) String dstType,
                                                @RequestParam(defaultValue = "1") Integer depth,
                                                @RequestParam(required = false) Integer status) {
        return Result.ok(kgAdminManager.neighbors(srcType, srcId, relType, dstType, depth, status));
    }
}
