package com.knowledgepilot.controller;

import com.knowledgepilot.common.ApiResponse;
import com.knowledgepilot.dto.CategoryStat;
import com.knowledgepilot.dto.KnowledgeDocQuery;
import com.knowledgepilot.dto.KnowledgeDocRequest;
import com.knowledgepilot.dto.PageResult;
import com.knowledgepilot.entity.KnowledgeDoc;
import com.knowledgepilot.service.KnowledgeDocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/docs")
@Tag(name = "知识文档", description = "KnowledgeDoc 增删改查")
public class KnowledgeDocController {

    private final KnowledgeDocService docService;

    @GetMapping
    @Operation(summary = "分页查询文档")
    public ApiResponse<PageResult<KnowledgeDoc>> page(@Valid KnowledgeDocQuery query) {
        return ApiResponse.ok(docService.page(query));
    }

    @GetMapping("/stats")
    @Operation(summary = "按分类统计")
    public ApiResponse<List<CategoryStat>> stats() {
        return ApiResponse.ok(docService.categoryStats());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询单个文档")
    public ApiResponse<KnowledgeDoc> get(@PathVariable long id) {
        return ApiResponse.ok(docService.get(id));
    }

    @PostMapping
    @Operation(summary = "新增文档")
    public ApiResponse<KnowledgeDoc> create(@RequestBody @Valid KnowledgeDocRequest request) {
        return ApiResponse.ok(docService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改文档")
    public ApiResponse<KnowledgeDoc> update(@PathVariable long id,
                                            @RequestBody @Valid KnowledgeDocRequest request) {
        return ApiResponse.ok(docService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档（逻辑删除）")
    public ApiResponse<Void> delete(@PathVariable long id) {
        docService.delete(id);
        return ApiResponse.ok();
    }
}
