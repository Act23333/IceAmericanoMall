package org.icedamericanomall.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UpdateProductReq implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private String mainImage;
    private String description;
    private String brand;
    private Long categoryId;
}
