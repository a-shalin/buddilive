package ca.digitalcave.buddi.live.controller;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.model.Category;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

@RestController
@RequestMapping("/data/categories/parents")
public class ParentsController {

	@Autowired
	private Sources sources;

	@GetMapping
	public String get(@AuthenticationPrincipal User user, @RequestParam(required = false) Integer exclude) {
		try {
			final List<Category> categories = Category.getHierarchy(sources.selectCategories(user));
			final Category category = (exclude == null ? null : sources.selectCategory(user, exclude));

			final JSONObject result = new JSONObject();
			final JSONArray data = new JSONArray();

			final JSONObject item = new JSONObject();
			item.put("value", "");
			item.put("text", LocaleUtil.getTranslation(user).getString("TOP_LEVEL"));
			data.put(item);

			getJsonArray(data, categories, category, user, 0);
			result.put("data", data);
			result.put("success", true);
			return result.toString();
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	private void getJsonArray(JSONArray array, List<Category> categories, Category exclude, User user, int depth) throws CryptoException {
		final StringBuilder sb = new StringBuilder();
		for (Category category : categories) {
			if (exclude != null && (category.getId().equals(exclude.getId()) || !category.getType().equals(exclude.getType()) || !category.getPeriodType().equals(exclude.getPeriodType()))) continue;
			final JSONObject item = new JSONObject();
			item.put("value", category.getId());
			if (category.isDeleted()) sb.append(" text-decoration: line-through;");
			if (!category.isIncome()) sb.append(" color: " + FormatUtil.HTML_RED + ";");
			item.put("style", sb.toString());
			sb.setLength(0);
			item.put("text", StringUtils.repeat("\u00a0", depth * 2) + CryptoUtil.decryptWrapper(category.getName(), user));
			item.put("income", category.isIncome());
			item.put("type", category.getType());
			item.put("periodType", category.getPeriodType());
			array.put(item);
			if (category.getChildren() != null) getJsonArray(array, category.getChildren(), exclude, user, depth + 1);
		}
	}
}