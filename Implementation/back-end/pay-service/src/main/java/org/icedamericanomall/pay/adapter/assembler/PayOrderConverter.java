package org.icedamericanomall.pay.adapter.assembler;

import org.icedamericanomall.integration.payment.JsapiPayResult;
import org.icedamericanomall.pay.domain.model.payorder.PayOrder;
import org.icedamericanomall.pay.adapter.response.JsapiPayParamsVO;
import org.icedamericanomall.pay.adapter.response.PayOrderVO;
import org.mapstruct.Mapper;

/**
 * 支付单 Domain ↔ VO 转换器。
 */
@Mapper(componentModel = "spring")
public interface PayOrderConverter {

    PayOrderVO toVO(PayOrder entity);

    /**
     * 渠道拉起参数集 → 小程序拉起应答（PAY7）：六字段一一对应，
     * {@code packageVal} 沿用 SDK 原名不做改名。
     *
     * @param result 渠道适配层返回的带签名拉起参数集
     * @return 前端直传 {@code wx.requestPayment} 的参数集；入参为 {@code null} 时返回 {@code null}
     */
    default JsapiPayParamsVO toParamsVO(JsapiPayResult result) {
        if (result == null) {
            return null;
        }
        JsapiPayParamsVO vo = new JsapiPayParamsVO();
        vo.setAppId(result.appId());
        vo.setTimeStamp(result.timeStamp());
        vo.setNonceStr(result.nonceStr());
        vo.setPackageVal(result.packageVal());
        vo.setSignType(result.signType());
        vo.setPaySign(result.paySign());
        return vo;
    }
}
