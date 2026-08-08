package org.noLazy.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OrderShippedEvent extends DomainEvent {
    private static final long serialVersionUID = 1L;
    private String orderNo;
    private Long userId;
    private String logisticsNumber;
    private String logisticsCompany;
}
