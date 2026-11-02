package com.bts.personalbudget.controller.category;

import com.bts.personalbudget.controller.category.config.CategoryControllerApiDocs;
import com.bts.personalbudget.core.domain.enumerator.CategoryStatus;
import com.bts.personalbudget.core.domain.enumerator.OperationType;
import com.bts.personalbudget.core.domain.model.Category;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.mapper.CategoryMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Category", description = "API for managing categories")
@RequiredArgsConstructor
@RequestMapping("/category")
@RestController
public class CategoryController implements CategoryControllerApiDocs {

    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;

    @Override
    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest categoryRequest) {
        log.info("m=create categoryRequest={}", categoryRequest);
        final Category category = categoryService.create(categoryMapper.toModel(categoryRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryMapper.toResponse(category));
    }

    @Override
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> find(
            @RequestParam(value = "operation_type", required = false) OperationType operationType,
            @RequestParam(value = "status", required = false) List<CategoryStatus> statuses,
            @RequestParam(value = "group_code", required = false) UUID groupCode) {
        log.info("m=find, operationType={}, statuses={}, groupCode={}", operationType, statuses, groupCode);
        final List<Category> categories = categoryService.find(operationType, statuses, groupCode);
        return ResponseEntity.ok(categoryMapper.toResponseList(categories));
    }

    @Override
    @GetMapping("/{code}")
    public ResponseEntity<CategoryResponse> findByCode(@PathVariable UUID code) {
        log.info("m=findByCode code={}", code);
        return ResponseEntity.ok(categoryMapper.toResponse(categoryService.findByCode(code)));
    }

    @Override
    @PatchMapping("/{code}")
    public ResponseEntity<CategoryResponse> update(@PathVariable UUID code,
                                                   @RequestBody @Valid CategoryUpdateRequest categoryUpdateRequest) {
        log.info("m=update code={} categoryUpdateRequest={}", code, categoryUpdateRequest);
        final Category category = categoryService.update(
                code, categoryUpdateRequest.name(), categoryUpdateRequest.groupCode());
        return ResponseEntity.ok(categoryMapper.toResponse(category));
    }

    @Override
    @PatchMapping("/{code}/inactivate")
    public ResponseEntity<CategoryResponse> inactivate(@PathVariable UUID code) {
        log.info("m=inactivate code={}", code);
        return ResponseEntity.ok(categoryMapper.toResponse(categoryService.inactivate(code)));
    }

    @Override
    @PatchMapping("/{code}/activate")
    public ResponseEntity<CategoryResponse> activate(@PathVariable UUID code) {
        log.info("m=activate code={}", code);
        return ResponseEntity.ok(categoryMapper.toResponse(categoryService.activate(code)));
    }

}
