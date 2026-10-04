package org.icedamericanomall.item.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.icedamericanomall.common.api.Result;
import org.icedamericanomall.common.security.UserContext;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.UnauthorizedException;
import org.icedamericanomall.item.adapter.request.FreightTemplateSaveReq;
import org.icedamericanomall.item.adapter.response.FreightTemplateVO;
import org.icedamericanomall.item.app.FreightTemplateManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家运费模板 Controller（F2 运费模型）—— 契约 {@code 05-API-Specification.md} §4.4.9。
 *
 * <p><b>路径审慎选择</b>：刻意用 {@code /api/item/freight-template/**} 而非
 * {@code /api/seller/freight-template} —— 网关 {@code /api/seller/**} 路由到 merchant-service，
 * 而模板归属商品域（item-service）；{@code /api/item/**} 已有路由，零网关改动。
 *
 * <p><b>身份兜底（fail-closed）</b>：{@code /api/item/**} 的 <b>GET</b> 在网关属匿名放行路径，
 * 故本 Controller 对「无登录身份」（{@code UserContext.getUserId()} 为 null）一律
 * 抛 {@code UNAUTHORIZED} —— 不把「匿名」静默当成「商家 ID 为空」，避免出现语义含混的空结果。
 * 商家自身携带 JWT 访问时网关仍会注入用户头，正常工作。
 */
@RestController
@RequestMapping("/api/item/freight-template")
@RequiredArgsConstructor
public class FreightTemplateController {

    private final FreightTemplateManager freightTemplateManager;

    /** 新建模板（FT1）。 */
    @PostMapping
    public Result<FreightTemplateVO> create(@Valid @RequestBody FreightTemplateSaveReq req) {
        return Result.ok(freightTemplateManager.create(requireSellerId(), req));
    }

    /** 编辑模板（FT2，局部更新）。 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody FreightTemplateSaveReq req) {
        freightTemplateManager.update(id, requireSellerId(), req);
        return Result.ok();
    }

    /** 当前商家的模板列表（FT3，含停用模板；按创建顺序）。 */
    @GetMapping("/list")
    public Result<List<FreightTemplateVO>> list() {
        return Result.ok(freightTemplateManager.listBySeller(requireSellerId()));
    }

    /** 删除模板（FT4，物理删除；绑定商品经 ON DELETE SET NULL 回落默认模板）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        freightTemplateManager.delete(id, requireSellerId());
        return Result.ok();
    }

    /** 商家身份兜底：无登录身份即拒绝（模板接口全部要求商家身份，规格标注 🏪）。 */
    private Long requireSellerId() {
        Long sellerId = UserContext.getUserId();
        if (sellerId == null) {
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "请先登录商家账号");
        }
        return sellerId;
    }
}
