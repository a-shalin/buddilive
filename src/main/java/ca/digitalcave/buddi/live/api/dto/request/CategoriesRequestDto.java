package ca.digitalcave.buddi.live.api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import ca.digitalcave.buddi.live.controller.Action;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategoriesRequestDto(
		Action action,
		Integer id,
		String uuid,
		String name,
		Boolean deleted,
		String type,
		String periodType,
		Integer parent,
		String date,
		Integer categoryId,
		String amount,
		String offset) {
}