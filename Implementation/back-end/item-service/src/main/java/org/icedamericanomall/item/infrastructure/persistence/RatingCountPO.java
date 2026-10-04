package org.icedamericanomall.item.infrastructure.persistence;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 评价星级分组投影（{@code SELECT rating, COUNT(*) ... GROUP BY rating} 的承载）——
 * 基础设施层外部投影，由仓储映射为领域读模型 {@code RatingCount}。
 */
@Data
public class RatingCountPO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Integer rating;

    private Long count;
}
