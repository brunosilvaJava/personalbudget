package com.bts.personalbudget.controller.category;

import com.bts.personalbudget.controller.category.config.CategoryGroupControllerApiDocs;
import com.bts.personalbudget.core.domain.service.category.CategoryService;
import com.bts.personalbudget.mapper.CategoryMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Tag(name = "Category Group", description = "API for listing category groups")
@RequiredArgsConstructor
@RequestMapping("/category_group")
@RestController
public class CategoryGroupController implements CategoryGroupControllerApiDocs {

    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;

    @Override
    @GetMapping
    public ResponseEntity<List<CategoryGroupResponse>> find() {
        log.info("m=find");
        return ResponseEntity.ok(categoryMapper.toGroupResponseList(categoryService.findGroups()));
    }

}
