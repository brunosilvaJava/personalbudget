package com.bts.personalbudget.controller.category.config;

import com.bts.personalbudget.controller.category.CategoryGroupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Category Group", description = "API for listing category groups")
public interface CategoryGroupControllerApiDocs {

    @Operation(
            summary = "List category groups",
            description = "Returns every group ordered by display_order. The list is not paginated: "
                    + "groups are a small fixed set that the front loads once.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of category groups")
    })
    @GetMapping
    ResponseEntity<List<CategoryGroupResponse>> find();

}
