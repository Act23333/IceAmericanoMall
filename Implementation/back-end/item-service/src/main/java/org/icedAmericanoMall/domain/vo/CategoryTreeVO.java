package org.icedAmericanoMall.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CategoryTreeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Integer sortOrder;
    private List<CategoryTreeVO> children;
}
