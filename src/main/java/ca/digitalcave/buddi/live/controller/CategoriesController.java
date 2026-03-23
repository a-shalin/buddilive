package ca.digitalcave.buddi.live.controller;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ca.digitalcave.buddi.live.db.Entries;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.Category;
import ca.digitalcave.buddi.live.model.CategoryPeriod;
import ca.digitalcave.buddi.live.model.CategoryPeriod.CategoryPeriods;
import ca.digitalcave.buddi.live.model.Entry;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.Transaction;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

@RestController
@RequestMapping("/data/categories")
public class CategoriesController {

	@Autowired
	private Sources sources;

	@Autowired
	private Transactions transactions;

	@Autowired
	private Entries entries;

	@Autowired
	private Crypto crypto;

	@GetMapping
	public String get(@AuthenticationPrincipal User user,
			@RequestParam String periodType,
			@RequestParam(required = false) String date,
			@RequestParam(defaultValue = "0") int offset) {
		try {
			final CategoryPeriod cp = new CategoryPeriod(CategoryPeriods.valueOf(periodType), FormatUtil.parseDateInternal(date), offset);
			final JSONObject result = new JSONObject();
			final JSONArray data = new JSONArray();

			final List<Category> categories = Category.getHierarchy(sources.selectCategories(user, cp));
			final List<Transaction> txns = transactions.selectTransactions(user, cp.getCurrentPeriodStartDate(), cp.getCurrentPeriodEndDate());

			for (Category c : categories) {
				final JSONObject category = getJsonObject(c, cp, txns, user);
				if (category != null) data.put(category);
			}

			result.put("period", FormatUtil.formatDate(cp.getCurrentPeriodStartDate(), user) + " - " + FormatUtil.formatDate(cp.getCurrentPeriodEndDate(), user));
			result.put("date", FormatUtil.formatDateInternal(cp.getCurrentPeriodStartDate()));
			result.put("previousPeriod", FormatUtil.formatDate(cp.getPreviousPeriodStartDate(), user) + " - " + FormatUtil.formatDate(cp.getPreviousPeriodEndDate(), user));
			result.put("children", data);
			result.put("success", true);
			return result.toString();
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	private JSONObject getJsonObject(Category category, CategoryPeriod categoryPeriod, List<Transaction> txns, User user) throws CryptoException {
		if (category.isDeleted() && !user.isShowDeleted()) return null;

		BigDecimal actualAmount = BigDecimal.ZERO;
		for (Transaction transaction : txns) {
			for (Split split : transaction.getSplits()) {
				if (split.getFromSource() == category.getId() || split.getToSource() == category.getId()) {
					actualAmount = actualAmount.add(CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true));
				}
			}
		}

		final JSONObject result = new JSONObject();
		result.put("id", category.getId());
		result.put("icon", "img/folder-open-table.png");
		result.put("date", FormatUtil.formatDateInternal(categoryPeriod.getCurrentPeriodStartDate()));
		result.put("type", categoryPeriod.getPeriodType());
		result.put("categoryType", category.getType());
		result.put("name", CryptoUtil.decryptWrapper(category.getName(), user));
		final StringBuilder sb = new StringBuilder();
		if (category.isDeleted()) sb.append(" text-decoration: line-through;");
		if (!category.isIncome()) sb.append(" color: " + FormatUtil.HTML_RED + ";");
		result.put("nameStyle", sb.toString());
		sb.setLength(0);

		final BigDecimal currentAmount = CryptoUtil.decryptWrapperBigDecimal(category.getCurrentEntry().getAmount(), user, true);
		result.put("current", FormatUtil.formatCurrency(currentAmount, user));
		result.put("currentStyle", (currentAmount.compareTo(BigDecimal.ZERO) == 0) ? FormatUtil.formatGray() : (FormatUtil.isRed(category, currentAmount) ? FormatUtil.formatRed() : ""));

		final BigDecimal previousAmount = CryptoUtil.decryptWrapperBigDecimal(category.getPreviousEntry().getAmount(), user, true);
		result.put("previous", FormatUtil.formatCurrency(previousAmount, user));
		result.put("previousStyle", (previousAmount.compareTo(BigDecimal.ZERO) == 0) ? FormatUtil.formatGray() : (FormatUtil.isRed(category, previousAmount) ? FormatUtil.formatRed() : ""));

		result.put("actual", FormatUtil.formatCurrency(actualAmount, user));
		result.put("actualStyle", (actualAmount.compareTo(BigDecimal.ZERO) == 0) ? FormatUtil.formatGray() : (FormatUtil.isRed(category, actualAmount) ? FormatUtil.formatRed() : ""));

		final BigDecimal differenceAmount = (actualAmount.subtract(currentAmount != null ? currentAmount : BigDecimal.ZERO));
		result.put("difference", FormatUtil.formatCurrency(category.isIncome() ? differenceAmount : differenceAmount.negate(), user));
		result.put("differenceStyle", (differenceAmount.compareTo(BigDecimal.ZERO) == 0) ? FormatUtil.formatGray() : (FormatUtil.isRed(category.isIncome() ? differenceAmount : differenceAmount.negate()) ? FormatUtil.formatRed() : ""));

		result.put("parent", category.getParent());
		result.put("deleted", category.isDeleted());

		final List<Category> children = category.getChildren();
		if (children != null) {
			Collections.sort(children, new Comparator<Category>() {
				@Override
				public int compare(Category o1, Category o2) {
					if (o1 == null || o2 == null) return 0;
					try {
						return CryptoUtil.decryptWrapper(o1.getName(), user).compareTo(CryptoUtil.decryptWrapper(o2.getName(), user));
					}
					catch (CryptoException e) {
						return 0;
					}
				}
			});
			for (Category child : children) {
				final JSONObject c = getJsonObject(child, categoryPeriod, txns, user);
				if (c != null) result.append("children", c);
			}
		}
		if (result.has("children")) {
			result.put("expanded", true);
		}
		else {
			result.put("leaf", true);
		}
		return result;
	}

	@PostMapping
	@Transactional
	public String post(@AuthenticationPrincipal User user, @RequestBody String body) {
		try {
			final JSONObject request = new JSONObject(body);
			final JSONObject result = new JSONObject();
			final String action = request.optString("action");

			final Category category = new Category(request);

			if ("insert".equals(action)) {
				ConstraintsChecker.checkInsertCategory(category, user, sources, crypto);
				final int count = sources.insertCategory(user, category);
				if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
			}
			else if ("delete".equals(action) || "undelete".equals(action)) {
				if (sources.selectSourceAssociatedCount(user, category) == 0) {
					final int count = sources.deleteSource(user, category);
					if (count != 1) throw new DatabaseException(String.format("Delete failed; expected 1 row, returned %s", count));
				}
				else {
					category.setDeleted("delete".equals(action));
					final int count = sources.updateSourceDeleted(user, category);
					if (count != 1) throw new DatabaseException(String.format("Delete / undelete failed; expected 1 row, returned %s", count));
				}
			}
			else if ("update".equals(action)) {
				ConstraintsChecker.checkUpdateCategory(category, user, sources, crypto);
				final int count = sources.updateCategory(user, category);
				if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
			}
			else if ("copyFromPrevious".equals(action)) {
				final CategoryPeriods period = CategoryPeriods.valueOf(request.getString("type"));
				final Date currentDate = period.getStartOfBudgetPeriod(FormatUtil.parseDateInternal(request.getString("date")));
				final Date previousDate = period.getBudgetPeriodOffset(currentDate, -1);

				final Map<Integer, Entry> previousEntries = entries.selectEntries(user, previousDate);
				final Map<Integer, Entry> currentEntries = entries.selectEntries(user, currentDate);
				for (Integer categoryId : previousEntries.keySet()) {
					if (CryptoUtil.decryptWrapperBigDecimal(previousEntries.get(categoryId).getAmount(), user, true).compareTo(BigDecimal.ZERO) != 0) {
						if (currentEntries.get(categoryId) == null) {
							final Entry entry = previousEntries.get(categoryId);
							entry.setDate(currentDate);
							ConstraintsChecker.checkInsertEntry(entry, user, sources, crypto);
							final int count = entries.insertEntry(user, entry);
							if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
						}
						else if (CryptoUtil.decryptWrapperBigDecimal(currentEntries.get(categoryId).getAmount(), user, true).compareTo(BigDecimal.ZERO) == 0) {
							final Entry entry = currentEntries.get(categoryId);
							entry.setAmount(previousEntries.get(categoryId).getAmount());
							ConstraintsChecker.checkUpdateEntry(entry, user, sources, entries, crypto);
							final int count = entries.updateEntry(user, entry);
							if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
						}
					}
				}
			}
			else if ("set".equals(action)) {
				final Entry entry = new Entry(request);
				final Entry existingEntry = entries.selectEntry(user, entry);

				if (existingEntry == null) {
					ConstraintsChecker.checkInsertEntry(entry, user, sources, crypto);
					final int count = entries.insertEntry(user, entry);
					if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
				}
				else {
					ConstraintsChecker.checkUpdateEntry(entry, user, sources, entries, crypto);
					final int count = entries.updateEntry(user, entry);
					if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
				}

				final CategoryPeriod cp = new CategoryPeriod(CategoryPeriods.valueOf(request.getString("periodType")), FormatUtil.parseDateInternal(request.getString("date")), Integer.parseInt(request.optString("offset", "0")));

				final Category c = sources.selectCategory(user, cp, request.getInt("categoryId"));
				final List<Transaction> txns = transactions.selectTransactions(user, c, cp.getCurrentPeriodStartDate(), cp.getCurrentPeriodEndDate());

				final JSONObject data = getJsonObject(c, cp, txns, user);
				result.put("data", data);
			}
			else {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
			}

			DataUpdater.updateBalances(user, sources, transactions, crypto);

			result.put("success", true);
			return result.toString();
		}
		catch (DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}