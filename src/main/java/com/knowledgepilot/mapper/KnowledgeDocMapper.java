package com.knowledgepilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgepilot.dto.CategoryStat;
import com.knowledgepilot.entity.KnowledgeDoc;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {

    /** XML 映射示例，见 resources/mapper/KnowledgeDocMapper.xml */
    List<CategoryStat> selectCategoryStats();
}
