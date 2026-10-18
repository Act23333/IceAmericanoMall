package org.icedamericanomall.item.adapter.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.utils.IdGenerator;
import org.icedamericanomall.common.web.QueryModel;
import org.icedamericanomall.item.adapter.request.CreateSpuReq;
import org.icedamericanomall.item.adapter.request.SpuPageReq;
import org.icedamericanomall.item.adapter.request.UpdateSpuReq;
import org.icedamericanomall.item.adapter.response.SpuVO;
import org.icedamericanomall.item.adapter.response.PairingCardVO;
import org.icedamericanomall.item.adapter.response.RecommendSpuVO;
import org.icedamericanomall.item.adapter.response.SkuVO;
import org.icedamericanomall.item.adapter.response.WxaCodeVO;
import org.icedamericanomall.item.app.SpuManager;
import org.icedamericanomall.item.app.kg.KgPairingManager;
import org.icedamericanomall.item.app.share.SpuShareManager;
import org.icedamericanomall.item.domain.model.spu.ShortCode;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SPU（商品）Controller — 京东标准：同一资源一个 Controller
 *
 * GET   /api/item/spu/page      — 游客/用户/VIP/SVIP 浏览商品列表
 * GET   /api/item/spu/{spuId}      — 商品详情（含完整 SKU）
 * GET   /api/item/spu/by-code/{code} — 商品详情（按<b>短码</b>，P38；小程序码落地页入口）
 * GET   /api/item/spu/{spuId}/skus  — SKU列表
 * GET   /api/item/spu/{spuId}/similar — 相似商品推荐（P27，向量）
 * GET   /api/item/spu/{spuId}/pairing — 搭配推荐卡（P35，知识图谱）
 * GET   /api/item/spu/{spuId}/wxa-code — 商品分享小程序码（P37，MinIO 缓存直返）
 * POST  /api/item/spu            — 商家发布商品
 * PUT   /api/item/spu/{spuId}       — 商家编辑商品
 * PUT   /api/item/spu/{spuId}/status — 商家/管理员上下架
 *
 * <p><b>资源段与路径变量口径（ADR-062 §决策 2/3、契约 §1.9）</b>：指向 SPU 实体的资源段
 * 一律 {@code spu}（与表 {@code spu}、聚合 {@code Spu}、业务键列 {@code spu.spu_id} 同词）；
 * 路径变量显式命名 {@code {spuId}} 且为 <b>32-hex 业务键</b>，不是技术键 —— 入口处经
 * {@link IdGenerator#requireWire} 做形态校验（非法即 400 {@code PARAM_ERROR} fail-fast），
 * 业务键 → 技术键的解析下沉到应用层/仓储（服务内，走 {@code uk_spu_id}）。
 */
@RestController
@RequestMapping("/api/item/spu")
@RequiredArgsConstructor
public class SpuController {

    private final SpuManager spuManager;
    private final KgPairingManager kgPairingManager;
    private final SpuShareManager spuShareManager;

    // ==================== 查询 (游客/用户/VIP/SVIP 共享) ====================

    @GetMapping("/page")
    public Result<IPage<SpuVO>> page(@QueryModel SpuPageReq req) {
        IPage<SpuVO> page = spuManager.pageSpus(
                req.getSellerId(), req.getCategoryId(), req.getKeyword(),
                req.getSort(), req.getOrder(),
                req.getPage(), req.getSize());
        return Result.ok(page);
    }

    @GetMapping("/{spuId}")
    public Result<SpuVO> detail(@PathVariable String spuId) {
        return Result.ok(spuManager.getSpuDetail(requireSpuId(spuId)));
    }

    /**
     * 商品详情 —— <b>按短码</b>（P38 {@code GET /api/item/spu/by-code/{code}}，V5.21 / 批次 B7b）。
     *
     * <p><b>为什么需要它</b>：小程序码 {@code scene} 只有 32 可见字符预算，而
     * {@code "p=" + 32-hex 业务键} = 34 字符（已超限 2），{@code scene} 里因此只能放<b>短码</b>；
     * 落地页用本端点把短码换回 {@code SpuVO}（与 P2 {@code GET /{spuId}} <b>逐字段同形</b>），
     * 再按返回的 {@code spu_id} 继续正常调用（详情/评价/相似等）。
     * <b>本端点是「短码 → 业务键」的唯一入口</b>，不做别名转发。
     *
     * <p><b>路由无歧义</b>：{@code by-code} 是<b>字面段</b>，Spring 的字面段优先级高于变量段，
     * 故 {@code GET /api/item/spu/by-code/8f3a1c9d20} 不会被 {@code /{spuId}} 吞掉；
     * 网关与 {@code SecurityConfig} 的 {@code /api/item/**} 通配已覆盖，<b>零网关改动</b>。
     *
     * <p><b>失败语义（契约 §4.4.1）</b>：短码形态非法 → 400 {@code PARAM_ERROR(1101)}
     * fail-fast（拼错的物料码应得「参数不对」，不是「商品不存在」）；短码不存在 / 商品已删除
     * → 404 {@code NO_RESOURCE_FOUND(1103)}；商品已下架 → 200 + {@code SpuVO}（含 {@code status}）。
     *
     * @param code 短码（8–10 位小写 hex —— <b>非业务键、非技术键</b>，故变量名为 {@code code}
     *             而不是 {@code spuId}：这串码不表达实体身份，只是一个承载位别名）
     */
    @GetMapping("/by-code/{code}")
    public Result<SpuVO> detailByCode(@PathVariable String code) {
        return Result.ok(spuManager.getSpuDetailByCode(ShortCode.require(code)));
    }

    /** SKU 列表（对外返回 VO，不泄漏 DO） */
    @GetMapping("/{spuId}/skus")
    public Result<List<SkuVO>> getSkuList(@PathVariable String spuId) {
        return Result.ok(spuManager.listSkus(requireSpuId(spuId)));
    }

    /**
     * 相似商品推荐（P27，ADR-046）—— 向量相似度降序，恒过滤在售并排除自身；
     * ES 关闭/无相似命中 → 空列表不报错（推荐位可空）。
     */
    @GetMapping("/{spuId}/similar")
    public Result<List<RecommendSpuVO>> similar(@PathVariable String spuId,
                                                @RequestParam(required = false) Integer size) {
        return Result.ok(spuManager.similarSpus(requireSpuId(spuId), size));
    }

    /**
     * 搭配推荐卡（P35，V5.16）—— 图关系召回为主（类目 PAIR_WITH 邻居）、向量相似兜底；
     * 三态 source=KG/FALLBACK/NONE，恒过滤在售、排除自身；全链路 fail-open 空态不报错。
     */
    @GetMapping("/{spuId}/pairing")
    public Result<PairingCardVO> pairing(@PathVariable String spuId,
                                         @RequestParam(required = false) Integer limit) {
        return Result.ok(kgPairingManager.pairing(requireSpuId(spuId), limit));
    }

    /**
     * 商品分享小程序码（P37，V5.18 契约 §4.4.x）—— 公开匿名分享场景；
     * MinIO 已有缓存直返 URL，否则调微信 {@code wxacode.getUnlimited} 生成并上传。
     * 商品不存在/不在售 → 1103；微信侧失败 fail-loud 透出。
     *
     * <p><b>{@code scene} 长度冲突已裁决（2026-10-18，B7b）</b>：微信 {@code scene} 上限 32
     * 可见字符，而 32-hex 业务键加 {@code p=} 前缀为 34 字符 → 超限。现 {@code scene} 存
     * <b>短码</b>（{@code p=<10 位 hex>} = 12 字符），落地页用 {@link #detailByCode(String)}（P38）
     * 换回 SPU。口径见 ADR-063 §决策 7/8 与契约 §1.9⑦-1/⑧。
     */
    @GetMapping("/{spuId}/wxa-code")
    public Result<WxaCodeVO> wxaCode(@PathVariable String spuId) {
        return Result.ok(spuShareManager.getWxaCode(requireSpuId(spuId)));
    }

    // ==================== 商家操作 ====================

    @PostMapping
    public Result<Void> create(@Valid @RequestBody CreateSpuReq req) {
        Long sellerId = UserContext.getUserId();
        spuManager.createSpu(sellerId, req);
        return Result.ok();
    }

    @PutMapping("/{spuId}")
    public Result<Void> update(@PathVariable String spuId, @RequestBody UpdateSpuReq req) {
        Long sellerId = UserContext.getUserId();
        spuManager.updateSpu(requireSpuId(spuId), sellerId, req);
        return Result.ok();
    }

    /**
     * 商家/管理员上下架（P5）—— 商家仅限自有商品，管理员放行。
     *
     * <p>归属校验在 {@code SpuManager#updateSpuStatus} 内完成（同一越权防护出口）；
     * 本方法只负责从登录上下文取操作人身份与管理员标记。
     *
     * <p><b>权限位（Q8，2026-10-18）</b>：管理员标记改用 {@code product:admin}（平台级商品配置），
     * 不再复用管理端总开关 {@code user:admin}。
     * <b>为什么必须改</b>：这里是**代码内判权**，注解扫描看不见 —— 若只改注解就停用 {@code user:admin}，
     * 管理员的上下架权限会**静默失效**（被当成非管理员，走归属校验后 403）。
     * 这类"代码内判权"的依赖，只有把扫描面覆盖到 {@code hasPermission(...)} 调用才能抓到
     * （见 {@code scripts/preflight-rbac-deprecated-bits.sh}）。
     * 行为等价性：管理员经 {@code *:*:*} 覆盖 {@code product:admin}，非管理员两者都不持 ⇒ 判定结果不变。
     */
    @PutMapping("/{spuId}/status")
    public Result<Void> updateStatus(@PathVariable String spuId, @RequestParam Integer status) {
        spuManager.updateSpuStatus(requireSpuId(spuId), UserContext.getUserId(),
                UserContext.hasPermission("product:admin"), status);
        return Result.ok();
    }

    /**
     * 路径变量形态校验（适配层唯一入参口，ADR-062 §决策 7.1）：
     * 合法返回归一化 32-hex 业务键，非法即 400 {@code PARAM_ERROR} fail-fast ——
     * 绝不让拼错的 id 静默落成 404/「查不到」。
     */
    private static String requireSpuId(String spuId) {
        return IdGenerator.requireWire("spuId", spuId);
    }
}
