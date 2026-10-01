package org.icedamericanomall.auth.domain.ability.risk;

import lombok.RequiredArgsConstructor;
import org.icedamericanomall.auth.domain.model.RiskAssessment;
import org.icedamericanomall.common.enums.ErrorCode;
import org.icedamericanomall.common.exception.BizException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 风控处置工厂（领域能力）—— 按优先级选取第一个命中的处置策略。
 */
@RequiredArgsConstructor
public class RiskHandlerFactory {

    private final List<RiskHandler> handlers;

    /**
     * 处置风险评估：按 {@link RiskHandler#getOrder()} 升序取第一个 supports 的处理器执行。
     *
     * @param assessment 风险评估结果
     * @throws BizException 命中阻断型处理器时抛出；无匹配处理器时抛出
     */
    public void handleRisk(RiskAssessment assessment) {
        List<RiskHandler> ordered = new ArrayList<>(handlers);
        ordered.sort(Comparator.comparingInt(RiskHandler::getOrder));
        RiskHandler target = ordered.stream()
                .filter(handler -> handler.supports(assessment))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR, "不支持的处理方式"));
        target.handle(assessment);
    }
}
