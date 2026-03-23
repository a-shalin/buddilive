package ca.digitalcave.buddi.live.controller;

import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.model.CategoryPeriod.CategoryPeriods;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.LocaleUtil;

@RestController
@RequestMapping("/data/categories/periods")
public class PeriodsController {

	@Autowired
	private Sources sources;

	@GetMapping
	public String get(@AuthenticationPrincipal User user) {
		final Set<String> categoryPeriods = new HashSet<>(sources.selectCategoryPeriods(user));

		final JSONArray data = new JSONArray();
		for (CategoryPeriods cp : CategoryPeriods.values()) {
			if (categoryPeriods.contains(cp.toString())) {
				final JSONObject item = new JSONObject();
				item.put("value", cp.toString());
				item.put("text", LocaleUtil.getTranslation(user).getString("BUDGET_CATEGORY_TYPE_" + cp.toString()));
				data.put(item);
			}
		}
		final JSONObject result = new JSONObject();
		result.put("data", data);
		result.put("success", true);
		return result.toString();
	}
}