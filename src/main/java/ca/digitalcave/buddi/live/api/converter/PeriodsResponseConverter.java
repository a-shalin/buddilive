package ca.digitalcave.buddi.live.api.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import ca.digitalcave.buddi.live.api.dto.PeriodsResponseDto;
import ca.digitalcave.buddi.live.api.dto.PeriodsResponseDto.PeriodItemDto;
import ca.digitalcave.buddi.live.model.CategoryPeriod.CategoryPeriods;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.LocaleUtil;

@Component
public class PeriodsResponseConverter {

	public PeriodsResponseDto convert(final User user, final Set<String> categoryPeriods) {
		final List<PeriodItemDto> data = new ArrayList<>();
		for (final CategoryPeriods categoryPeriod : CategoryPeriods.values()) {
			if (categoryPeriods.contains(categoryPeriod.toString())) {
				data.add(new PeriodItemDto(
						categoryPeriod.toString(),
						LocaleUtil.getTranslation(user).getString("BUDGET_CATEGORY_TYPE_" + categoryPeriod)));
			}
		}
		return new PeriodsResponseDto(true, data);
	}
}
