package org.icedamericanomall.fallback;

import lombok.extern.slf4j.Slf4j;
import org.icedamericanomall.client.FreightTemplateClient;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;
import org.icedamericanomall.dto.FreightTemplateCO;
import org.icedamericanomall.dto.FreightTemplateQueryCO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 运费模板查询降级工厂 —— <b>fail-closed（抛异常）</b>，属 ADR-011 的 <b>② 判别型查询</b>。
 *
 * <p>本查询的返回值会被下单计价解释为业务事实（「商家无启用模板」→ {@code postFee = 0} 免邮）。
 * 若降级返回空集合，则 item-service 抖动会被伪装成「免邮」这一业务结论——平台资损且无告警，
 * 属典型的错误信息污染。因此降级必须抛异常，使下单 fail-closed（不阻断重试，但不静默免邮）。
 *
 * <p>⚠️ 与契约 §4.14.17 草拟口径（降级为空集合 → postFee=0）的偏差已在
 * {@link FreightTemplateClient} 类注释中登记。
 */
@Slf4j
@Component
public class FreightTemplateClientFallback implements FallbackFactory<FreightTemplateClient> {

    @Override
    public FreightTemplateClient create(Throwable cause) {
        return new FreightTemplateClient() {

            @Override
            public List<FreightTemplateCO> query(FreightTemplateQueryCO query) {
                log.error("运费模板查询降级：item-service 不可用（禁止降级为空集合，"
                                + "免邮会被误判为业务事实导致资损） sellerId={}, templateIds={}",
                        query == null ? null : query.getSellerId(),
                        query == null ? null : query.getTemplateIds(), cause);
                throw new BizException(ErrorCode.SYSTEM_BUSY, "运费计算服务暂不可用，请稍后重试");
            }
        };
    }
}
