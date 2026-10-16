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
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SPU（商品）Controller — 京东标准：同一资源一个 Controller
 *
 * GET   /api/item/spu/page      — 游客/用户/VIP/SVIP 浏览商品列表
 * GET   /api/item/spu/{spuId}      — 商品详情（含完整 SKU）
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
     * <p><b>P37 {@code scene} 长度冲突（未裁决，显式登记）</b>：微信 {@code scene} 上限 32
     * 可见字符，而 32-hex 业务键加 {@code p=} 前缀为 34 字符 → 超限；本批（ADR-062 落码批）
     * 不引入短码/短链映射（属承载层设计），也<b>不做截断</b>（截断会指向错商品）。
     * 现实现细节与登记见 {@code SpuShareManager}。
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
     */
    @PutMapping("/{spuId}/status")
    public Result<Void> updateStatus(@PathVariable String spuId, @RequestParam Integer status) {
        spuManager.updateSpuStatus(requireSpuId(spuId), UserContext.getUserId(),
                UserContext.hasPermission("user:admin"), status);
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
