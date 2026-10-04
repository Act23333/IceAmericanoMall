package org.icedamericanomall.logistics.adapter.assembler;

import org.icedamericanomall.logistics.adapter.response.LogisticsVO;
import org.icedamericanomall.logistics.domain.model.logistics.Package;
import org.springframework.stereotype.Component;

/**
 * 物流对象装配器 —— 聚合 → VO 的字段映射集中在此，Controller 不手写转换。
 * <p>物流服务未引入 MapStruct（无处理器配置），故按字段显式装配，语义等价且无额外构建依赖。
 */
@Component
public class LogisticsConverter {

    /**
     * 物流聚合 → 视图对象。
     *
     * @param pkg 物流包裹聚合，可为 {@code null}
     * @return 视图对象；入参为 {@code null} 时返回 {@code null}
     */
    public LogisticsVO toVO(Package pkg) {
        if (pkg == null) {
            return null;
        }
        LogisticsVO vo = new LogisticsVO();
        vo.setOrderId(pkg.getOrderId());
        vo.setPackageNo(pkg.getPackageNo()); // V5.6（F3）：包裹号
        vo.setLogisticsNumber(pkg.getLogisticsNumber());
        vo.setLogisticsCompany(pkg.getLogisticsCompany());
        vo.setContact(pkg.getContact());
        vo.setMobile(pkg.getMobile());
        vo.setProvince(pkg.getProvince());
        vo.setCity(pkg.getCity());
        vo.setDistrict(pkg.getDistrict());
        vo.setStreet(pkg.getStreet());
        vo.setDetail(pkg.getDetail());
        vo.setStatus(pkg.getStatus());
        vo.setCreateTime(pkg.getCreateTime());
        return vo;
    }
}
