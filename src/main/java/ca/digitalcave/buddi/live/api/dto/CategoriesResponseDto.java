package ca.digitalcave.buddi.live.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record CategoriesResponseDto(
		boolean success,
		String period,
		String date,
		String previousPeriod,
		List<CategoryNodeDto> children) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record CategoryNodeDto(
			int id,
			String icon,
			String date,
			String type,
			String categoryType,
			String name,
			String nameStyle,
			String current,
			String currentStyle,
			String previous,
			String previousStyle,
			String actual,
			String actualStyle,
			String difference,
			String differenceStyle,
			Integer parent,
			boolean deleted,
			Boolean expanded,
			Boolean leaf,
			List<CategoryNodeDto> children) {
	}
}
