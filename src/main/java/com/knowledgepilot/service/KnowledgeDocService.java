package com.knowledgepilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledgepilot.common.BizException;
import com.knowledgepilot.dto.CategoryStat;
import com.knowledgepilot.dto.KnowledgeDocQuery;
import com.knowledgepilot.dto.KnowledgeDocRequest;
import com.knowledgepilot.dto.PageResult;
import com.knowledgepilot.entity.KnowledgeDoc;
import com.knowledgepilot.mapper.KnowledgeDocMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class KnowledgeDocService {

    private final KnowledgeDocMapper docMapper;

    @Transactional(readOnly = true)
    public PageResult<KnowledgeDoc> page(KnowledgeDocQuery query) {
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<KnowledgeDoc>()
                .like(StringUtils.hasText(query.getKeyword()), KnowledgeDoc::getTitle, query.getKeyword())
                .eq(StringUtils.hasText(query.getCategory()), KnowledgeDoc::getCategory, query.getCategory())
                .eq(query.getStatus() != null, KnowledgeDoc::getStatus, query.getStatus())
                .orderByDesc(KnowledgeDoc::getId);
        Page<KnowledgeDoc> page = Page.of(query.getPage(), query.getSize());
        return PageResult.of(docMapper.selectPage(page, wrapper));
    }

    @Transactional(readOnly = true)
    public KnowledgeDoc get(long id) {
        return require(id);
    }

    @Transactional
    public KnowledgeDoc create(KnowledgeDocRequest request) {
        KnowledgeDoc doc = new KnowledgeDoc();
        apply(doc, request);
        docMapper.insert(doc);
        return doc;
    }

    @Transactional
    public KnowledgeDoc update(long id, KnowledgeDocRequest request) {
        KnowledgeDoc doc = require(id);
        apply(doc, request);
        docMapper.updateById(doc);
        return require(id);
    }

    @Transactional
    public void delete(long id) {
        if (docMapper.deleteById(id) == 0) {
            throw new BizException(404, "文档不存在: " + id);
        }
    }

    @Transactional(readOnly = true)
    public List<CategoryStat> categoryStats() {
        return docMapper.selectCategoryStats();
    }

    private KnowledgeDoc require(long id) {
        KnowledgeDoc doc = docMapper.selectById(id);
        if (doc == null) {
            throw new BizException(404, "文档不存在: " + id);
        }
        return doc;
    }

    private void apply(KnowledgeDoc doc, KnowledgeDocRequest request) {
        doc.setTitle(request.title());
        doc.setContent(request.content());
        doc.setCategory(StringUtils.hasText(request.category()) ? request.category() : "general");
        doc.setTags(request.tags());
        doc.setStatus(request.status());
    }
}
