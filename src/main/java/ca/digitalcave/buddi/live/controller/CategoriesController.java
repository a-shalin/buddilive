package ca.digitalcave.buddi.live.controller;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

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

import ca.digitalcave.buddi.live.api.converter.CategoriesResponseConverter;
import ca.digitalcave.buddi.live.api.dto.CategoriesMutationResponseDto;
import ca.digitalcave.buddi.live.api.dto.CategoriesResponseDto;
import ca.digitalcave.buddi.live.api.dto.CategoriesResponseDto.CategoryNodeDto;
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

	@Autowired
	private CategoriesResponseConverter categoriesResponseConverter;

	@GetMapping
	public CategoriesResponseDto get(@AuthenticationPrincipal final User user,
			@RequestParam final String periodType,
			@RequestParam(required = false) final String date,
			@RequestParam(defaultValue = "0") final int offset) {
		try {
			final CategoryPeriod cp = new CategoryPeriod(CategoryPeriods.valueOf(periodType), FormatUtil.parseDateInternal(date), offset);
			final List<Category> categories = Category.getHierarchy(sources.selectCategories(user, cp));
			final List<Transaction> txns = transactions.selectTransactions(user, cp.getCurrentPeriodStartDate(), cp.getCurrentPeriodEndDate());
			return categoriesResponseConverter.convert(user, cp, categories, txns);
		}
		catch (final CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	@PostMapping
	@Transactional
	public CategoriesMutationResponseDto post(@AuthenticationPrincipal final User user, @RequestBody final String body) {
		try {
			final JSONObject request = new JSONObject(body);
			final Action action = Action.fromString(request.optString("action"));
			final Category category = new Category(request);
			CategoryNodeDto data = null;

			if (Action.INSERT == action) {
				ConstraintsChecker.checkInsertCategory(category, user, sources, crypto);
				final int count = sources.insertCategory(user, category);
				if (count != 1) {
					throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
				}
			}
			else if (Action.DELETE == action || Action.UNDELETE == action) {
				if (sources.selectSourceAssociatedCount(user, category) == 0) {
					final int count = sources.deleteSource(user, category);
					if (count != 1) {
						throw new DatabaseException(String.format("Delete failed; expected 1 row, returned %s", count));
					}
				}
				else {
					category.setDeleted(Action.DELETE == action);
					final int count = sources.updateSourceDeleted(user, category);
					if (count != 1) {
						throw new DatabaseException(String.format("Delete / undelete failed; expected 1 row, returned %s", count));
					}
				}
			}
			else if (Action.UPDATE == action) {
				ConstraintsChecker.checkUpdateCategory(category, user, sources, crypto);
				final int count = sources.updateCategory(user, category);
				if (count != 1) {
					throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
				}
			}
			else if (Action.COPY_FROM_PREVIOUS == action) {
				final CategoryPeriods period = CategoryPeriods.valueOf(request.getString("type"));
				final Date currentDate = period.getStartOfBudgetPeriod(FormatUtil.parseDateInternal(request.getString("date")));
				final Date previousDate = period.getBudgetPeriodOffset(currentDate, -1);

				final Map<Integer, Entry> previousEntries = entries.selectEntries(user, previousDate);
				final Map<Integer, Entry> currentEntries = entries.selectEntries(user, currentDate);
				for (final Integer categoryId : previousEntries.keySet()) {
					if (CryptoUtil.decryptWrapperBigDecimal(previousEntries.get(categoryId).getAmount(), user, true).compareTo(BigDecimal.ZERO) != 0) {
						if (currentEntries.get(categoryId) == null) {
							final Entry entry = previousEntries.get(categoryId);
							entry.setDate(currentDate);
							ConstraintsChecker.checkInsertEntry(entry, user, sources, crypto);
							final int count = entries.insertEntry(user, entry);
							if (count != 1) {
								throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
							}
						}
						else if (CryptoUtil.decryptWrapperBigDecimal(currentEntries.get(categoryId).getAmount(), user, true).compareTo(BigDecimal.ZERO) == 0) {
							final Entry entry = currentEntries.get(categoryId);
							entry.setAmount(previousEntries.get(categoryId).getAmount());
							ConstraintsChecker.checkUpdateEntry(entry, user, sources, entries, crypto);
							final int count = entries.updateEntry(user, entry);
							if (count != 1) {
								throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
							}
						}
					}
				}
			}
			else if (Action.SET == action) {
				final Entry entry = new Entry(request);
				final Entry existingEntry = entries.selectEntry(user, entry);

				if (existingEntry == null) {
					ConstraintsChecker.checkInsertEntry(entry, user, sources, crypto);
					final int count = entries.insertEntry(user, entry);
					if (count != 1) {
						throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
					}
				}
				else {
					ConstraintsChecker.checkUpdateEntry(entry, user, sources, entries, crypto);
					final int count = entries.updateEntry(user, entry);
					if (count != 1) {
						throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
					}
				}

				final CategoryPeriod cp = new CategoryPeriod(
						CategoryPeriods.valueOf(request.getString("periodType")),
						FormatUtil.parseDateInternal(request.getString("date")),
						Integer.parseInt(request.optString("offset", "0")));

				final Category c = sources.selectCategory(user, cp, request.getInt("categoryId"));
				final List<Transaction> txns = transactions.selectTransactions(user, c, cp.getCurrentPeriodStartDate(), cp.getCurrentPeriodEndDate());
				data = categoriesResponseConverter.convertCategoryNode(c, cp, txns, user);
			}
			else {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
			}

			DataUpdater.updateBalances(user, sources, transactions, crypto);
			return new CategoriesMutationResponseDto(true, data);
		}
		catch (final DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
		catch (final CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}
