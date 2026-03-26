package ca.digitalcave.buddi.live.controller;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.digitalcave.buddi.live.api.converter.PeriodsResponseConverter;
import ca.digitalcave.buddi.live.api.dto.PeriodsResponseDto;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.model.User;

@RestController
@RequestMapping("/data/categories/periods")
public class PeriodsController {

	@Autowired
	private Sources sources;

	@Autowired
	private PeriodsResponseConverter periodsResponseConverter;

	@GetMapping
	public PeriodsResponseDto get(@AuthenticationPrincipal final User user) {
		final Set<String> categoryPeriods = new HashSet<>(sources.selectCategoryPeriods(user));
		return periodsResponseConverter.convert(user, categoryPeriods);
	}
}
