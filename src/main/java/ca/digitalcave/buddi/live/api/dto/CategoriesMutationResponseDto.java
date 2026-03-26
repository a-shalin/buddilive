package ca.digitalcave.buddi.live.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategoriesMutationResponseDto(boolean success, CategoriesResponseDto.CategoryNodeDto data) {
}
