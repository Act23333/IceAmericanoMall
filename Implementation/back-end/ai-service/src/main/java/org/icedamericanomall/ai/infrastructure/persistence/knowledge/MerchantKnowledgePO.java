package org.icedamericanomall.ai.infrastructure.persistence.knowledge;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家知识库表映射对象（PO）—— {@code merchant_knowledge_base} 表
 * （表所有权随客服域迁入本服务，ADR-016 收尾）。
 */
@Data
@TableName("merchant_knowledge_base")
public class MerchantKnowledgePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sellerId;
    private String title;
    private String content;
    private String category;
    private String embeddingId;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
