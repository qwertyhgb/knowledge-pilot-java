package com.knowledgepilot.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("knowledge_doc")
@Schema(description = "知识文档")
public class KnowledgeDoc {

    @Schema(description = "主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文内容")
    private String content;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "标签，英文逗号分隔")
    private String tags;

    @Schema(description = "状态：0-草稿，1-已发布")
    private Integer status;

    @TableLogic
    @TableField(select = false)
    @Schema(description = "逻辑删除标记", hidden = true)
    private Integer deleted;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
