package ca.digitalcave.buddi.live.controller;

import ca.digitalcave.buddi.live.api.converter.ReportResponseConverter;
import ca.digitalcave.buddi.live.api.dto.ReportDataResponseDto;
import ca.digitalcave.buddi.live.db.Entries;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.model.*;
import ca.digitalcave.buddi.live.model.CategoryPeriod.CategoryPeriods;
import ca.digitalcave.buddi.live.model.report.Interval;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.common.DateUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/data/report")
public class ReportController {

	@Autowired
	private Sources sources;

	@Autowired
	private Transactions transactions;

	@Autowired
	private Entries entries;

	@Autowired
	private ReportResponseConverter reportResponseConverter;

	private Date[] processInterval(final String interval, final String startDate, final String endDate) {
		final Interval i = Interval.valueOf(interval);
		if (i == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An interval parameter is required.");
		if (i != Interval.PLUGIN_FILTER_OTHER) {
			return new Date[]{i.getStartDate(), i.getEndDate()};
		}
		else {
			final Date start = FormatUtil.parseDateInternal(startDate);
			final Date end = FormatUtil.parseDateInternal(endDate);
			if (start == null || end == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "For PLUGIN_FILTER_OTHER intervals, startDate and endDate parameters are required.");
			return new Date[]{start, end};
		}
	}

	@GetMapping("/pietotalsbycategory")
	public ReportDataResponseDto pieTotalsByCategory(@AuthenticationPrincipal final User user,
			@RequestParam final String interval,
			@RequestParam final String type,
			@RequestParam(required = false) final String startDate,
			@RequestParam(required = false) final String endDate) {
		try {
			if (!"E".equals(type) && !"I".equals(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type parameter (I or E) is required");

			final Date[] dates = processInterval(interval, startDate, endDate);
			final Map<Integer, BigDecimal> totalsByCategory = new HashMap<>();
			final Map<Integer, String> labelsByCategory = new HashMap<>();
			final List<Transaction> txns = transactions.selectTransactions(user, type, dates[0], dates[1]);
			for (Transaction transaction : txns) {
				for (Split split : transaction.getSplits()) {
					final Integer categoryId = type.equals(split.getFromType()) ? split.getFromSource() : split.getToSource();
					final BigDecimal amount = CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true);
					totalsByCategory.put(categoryId, totalsByCategory.get(categoryId) == null ? amount : totalsByCategory.get(categoryId).add(amount));

					if (labelsByCategory.get(categoryId) == null) {
						labelsByCategory.put(categoryId, type.equals(split.getFromType()) ? split.getFromSourceName() : split.getToSourceName());
					}
				}
			}
			BigDecimal total = BigDecimal.ZERO;
			for (BigDecimal subtotal : totalsByCategory.values()) {
				total = total.add(subtotal);
			}
			total = total.divide(new BigDecimal(100));

			final List<Integer> categories = new ArrayList<>(totalsByCategory.keySet());
			Collections.sort(categories, new Comparator<Integer>() {
				@Override
				public int compare(final Integer o1, final Integer o2) {
					return -1 * totalsByCategory.get(o1).compareTo(totalsByCategory.get(o2));
				}
			});

			final List<Map<String, Object>> data = new ArrayList<>();
			for (Integer categoryId : categories) {
				final Map<String, Object> object = new LinkedHashMap<>();
				final BigDecimal amount = totalsByCategory.get(categoryId);
				object.put("label", CryptoUtil.decryptWrapper(labelsByCategory.get(categoryId), user) + " - " + FormatUtil.formatCurrency(amount, user));
				object.put("amount", amount);
				object.put("formattedAmount", FormatUtil.formatCurrency(amount, user));
				object.put("percent", amount.divide(total, RoundingMode.HALF_UP));
				data.add(object);
			}

			return reportResponseConverter.convert(data);
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	@GetMapping("/incomeandexpensesbycategory")
	public ReportDataResponseDto incomeAndExpensesByCategory(@AuthenticationPrincipal final User user,
			@RequestParam final String interval,
			@RequestParam(required = false) final String startDate,
			@RequestParam(required = false) final String endDate) {
		try {
			final Date[] dates = processInterval(interval, startDate, endDate);
			final List<Transaction> txns = transactions.selectTransactions(user, dates[0], dates[1]);

			final List<Map<String, Object>> data = new ArrayList<>();

			final BigDecimal[] income = calculateIncomeExpenseCategories(data, true, user, sources.selectCategories(user, true), txns, dates);
			final BigDecimal[] expenses = calculateIncomeExpenseCategories(data, false, user, sources.selectCategories(user, false), txns, dates);

			final BigDecimal totalActual = income[0].add(expenses[0]);
			final BigDecimal totalBudgeted = income[1].add(expenses[1]);

			final Map<String, Object> object = new LinkedHashMap<>();
			object.put("source", LocaleUtil.getTranslation(user).getString("TOTAL"));
			object.put("sourceStyle", "font-weight: bold;");
			object.put("actual", FormatUtil.formatCurrency(totalActual, user));
			object.put("actualStyle", (FormatUtil.isRed(totalActual) ? FormatUtil.formatRed() : "") + " font-weight: bold; ");
			object.put("budgeted", FormatUtil.formatCurrency(totalBudgeted, user));
			object.put("budgetedStyle", (FormatUtil.isRed(totalBudgeted) ? FormatUtil.formatRed() : "") + " font-weight: bold; ");
			final BigDecimal difference = (totalActual.subtract(totalBudgeted != null ? totalBudgeted : BigDecimal.ZERO));
			object.put("difference", FormatUtil.formatCurrency(difference, user));
			object.put("differenceStyle", (FormatUtil.isRed(difference) ? FormatUtil.formatRed() : "") + " font-weight: bold; ");
			data.add(object);

			return reportResponseConverter.convert(data);
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	private BigDecimal[] calculateIncomeExpenseCategories(final List<Map<String, Object>> data, final boolean income, final User user, final List<Category> categories, final List<Transaction> txns, final Date[] dates) throws CryptoException {
		Collections.sort(categories, new Comparator<Category>() {
			@Override
			public int compare(final Category o1, final Category o2) {
				if (o1 == null || o2 == null) return 0;
				if (o1.isIncome() != o2.isIncome()) return o1.isIncome() ? -1 : 1;
				try {
					return CryptoUtil.decryptWrapper(o1.getName(), user).compareTo(CryptoUtil.decryptWrapper(o2.getName(), user));
				}
				catch (CryptoException e) {
					return 0;
				}
			}
		});

		final Map<Integer, List<Transaction>> transactionsBySource = new HashMap<>();
		final Map<Integer, BigDecimal> totalsBySource = new HashMap<>();
		for (Transaction transaction : txns) {
			for (Split split : transaction.getSplits()) {
				if ("I".equals(split.getFromType()) || "E".equals(split.getFromType())) {
					final int source = split.getFromSource();
					if (transactionsBySource.get(source) == null) transactionsBySource.put(source, new ArrayList<>());
					final Transaction t = new Transaction();
					t.setDate(transaction.getDate());
					t.setDescription(transaction.getDescription());
					t.setNumber(transaction.getNumber());
					t.setSplits(new ArrayList<>());
					t.getSplits().add(split);
					transactionsBySource.get(source).add(t);
					final BigDecimal splitAmount = CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true);
					totalsBySource.put(source, splitAmount.add(totalsBySource.get(source) == null ? BigDecimal.ZERO : totalsBySource.get(source)));
				}
				else if ("I".equals(split.getToType()) || "E".equals(split.getToType())) {
					final int source = split.getToSource();
					if (transactionsBySource.get(source) == null) transactionsBySource.put(source, new ArrayList<>());
					final Transaction t = new Transaction();
					t.setDate(transaction.getDate());
					t.setDescription(transaction.getDescription());
					t.setNumber(transaction.getNumber());
					t.setSplits(new ArrayList<>());
					t.getSplits().add(split);
					transactionsBySource.get(source).add(t);
					final BigDecimal splitAmount = CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true);
					totalsBySource.put(source, splitAmount.add(totalsBySource.get(source) == null ? BigDecimal.ZERO : totalsBySource.get(source)));
				}
			}
		}

		BigDecimal totalActual = BigDecimal.ZERO;
		BigDecimal totalBudgeted = BigDecimal.ZERO;
		for (Category category : categories) {
			final BigDecimal budgetedAmount = category.getAmount(user, entries, dates[0], dates[1]);
			final BigDecimal actualAmount = totalsBySource.get(category.getId()) == null ? BigDecimal.ZERO : totalsBySource.get(category.getId());

			totalActual = totalActual.add(category.isIncome() ? actualAmount : actualAmount.negate());
			totalBudgeted = totalBudgeted.add(category.isIncome() ? budgetedAmount : budgetedAmount.negate());
			if (budgetedAmount.compareTo(BigDecimal.ZERO) != 0 || actualAmount.compareTo(BigDecimal.ZERO) != 0) {
				final Map<String, Object> object = new LinkedHashMap<>();
				object.put("source", CryptoUtil.decryptWrapper(category.getName(), user));
				object.put("actual", FormatUtil.formatCurrency(actualAmount, user));
				object.put("actualStyle", (FormatUtil.isRed(category, actualAmount) ? FormatUtil.formatRed() : ""));
				object.put("budgeted", FormatUtil.formatCurrency(budgetedAmount, user));
				object.put("budgetedStyle", (FormatUtil.isRed(category, budgetedAmount) ? FormatUtil.formatRed() : ""));
				final BigDecimal diff = (actualAmount.subtract(budgetedAmount != null ? budgetedAmount : BigDecimal.ZERO));
				object.put("difference", FormatUtil.formatCurrency(category.isIncome() ? diff : diff.negate(), user));
				object.put("differenceStyle", (FormatUtil.isRed(category.isIncome() ? diff : diff.negate()) ? FormatUtil.formatRed() : ""));

				final List<Transaction> transactionsInCategory = transactionsBySource.get(category.getId());
				if (transactionsInCategory != null) {
					Collections.sort(transactionsInCategory, new Comparator<Transaction>() {
						@Override
						public int compare(final Transaction o1, final Transaction o2) {
							if (o1 == null || o2 == null) return 0;
							return o1.getDate().compareTo(o2.getDate());
						}
					});
					final List<Map<String, Object>> ts = new ArrayList<>();
					for (Transaction t : transactionsInCategory) {
						final Map<String, Object> o = new LinkedHashMap<>();
						o.put("date", FormatUtil.formatDate(t.getDate(), user));
						o.put("description", CryptoUtil.decryptWrapper(t.getDescription(), user));
						o.put("number", CryptoUtil.decryptWrapper(t.getNumber(), user));
						final Split s = t.getSplits().get(0);
						o.put("from", CryptoUtil.decryptWrapper(s.getFromSourceName(), user));
						o.put("to", CryptoUtil.decryptWrapper(s.getToSourceName(), user));
						final BigDecimal splitAmount = CryptoUtil.decryptWrapperBigDecimal(s.getAmount(), user, true);
						o.put("amount", FormatUtil.formatCurrency(splitAmount, user));
						o.put("amountStyle", (FormatUtil.isRed(category, splitAmount) ? FormatUtil.formatRed() : ""));
						ts.add(o);
					}
					object.put("transactions", ts);
				}
				data.add(object);
			}
		}

		final Map<String, Object> object = new LinkedHashMap<>();
		object.put("source", LocaleUtil.getTranslation(user).getString(income ? "TOTAL_INCOME" : "TOTAL_EXPENSES"));
		object.put("sourceStyle", "font-weight: bold;");
		object.put("actual", FormatUtil.formatCurrency(totalActual, user));
		object.put("actualStyle", (FormatUtil.isRed(totalActual) ? FormatUtil.formatRed() : "") + " font-weight: bold; ");
		object.put("budgeted", FormatUtil.formatCurrency(totalBudgeted, user));
		object.put("budgetedStyle", (FormatUtil.isRed(totalBudgeted) ? FormatUtil.formatRed() : "") + " font-weight: bold; ");
		final BigDecimal diff = (totalActual.subtract(totalBudgeted != null ? totalBudgeted : BigDecimal.ZERO));
		object.put("difference", FormatUtil.formatCurrency(diff, user));
		object.put("differenceStyle", (FormatUtil.isRed(diff) ? FormatUtil.formatRed() : "") + " font-weight: bold; ");
		data.add(object);

		return new BigDecimal[]{totalActual, totalBudgeted};
	}

	@GetMapping("/averageincomeandexpensesbycategory")
	public ReportDataResponseDto averageIncomeAndExpensesByCategory(@AuthenticationPrincipal final User user,
			@RequestParam final String interval,
			@RequestParam(required = false) final String startDate,
			@RequestParam(required = false) final String endDate) {
		try {
			final Date[] dates = processInterval(interval, startDate, endDate);
			final List<Transaction> txns = transactions.selectTransactions(user, dates[0], dates[1]);

			final List<Map<String, Object>> data = new ArrayList<>();

			calculateAverageCategories(data, true, user, sources.selectCategories(user, true), txns, dates);
			calculateAverageCategories(data, false, user, sources.selectCategories(user, false), txns, dates);

			return reportResponseConverter.convert(data);
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	private void calculateAverageCategories(final List<Map<String, Object>> data, final boolean income, final User user, final List<Category> categories, final List<Transaction> txns, final Date[] dates) throws CryptoException {
		Collections.sort(categories, new Comparator<Category>() {
			@Override
			public int compare(final Category o1, final Category o2) {
				if (o1 == null || o2 == null) return 0;
				if (o1.isIncome() != o2.isIncome()) return o1.isIncome() ? -1 : 1;
				try {
					return CryptoUtil.decryptWrapper(o1.getName(), user).compareTo(CryptoUtil.decryptWrapper(o2.getName(), user));
				}
				catch (CryptoException e) {
					return 0;
				}
			}
		});

		final Map<Integer, BigDecimal> totalsBySource = new HashMap<>();
		for (Transaction transaction : txns) {
			for (Split split : transaction.getSplits()) {
				if ("I".equals(split.getFromType()) || "E".equals(split.getFromType())) {
					final int source = split.getFromSource();
					final BigDecimal splitAmount = CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true);
					totalsBySource.put(source, splitAmount.add(totalsBySource.get(source) == null ? BigDecimal.ZERO : totalsBySource.get(source)));
				}
				else if ("I".equals(split.getToType()) || "E".equals(split.getToType())) {
					final int source = split.getToSource();
					final BigDecimal splitAmount = CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true);
					totalsBySource.put(source, splitAmount.add(totalsBySource.get(source) == null ? BigDecimal.ZERO : totalsBySource.get(source)));
				}
			}
		}

		final Map<String, BigDecimal> totalActualByPeriod = new TreeMap<>();
		final Map<String, BigDecimal> totalBudgetedByPeriod = new TreeMap<>();
		final int totalDaysInRange = DateUtil.getDaysBetween(dates[0], dates[1], true);

		for (Category category : categories) {
			final BigDecimal budgetedAmount = category.getAmount(user, entries, dates[0], dates[1]);
			final BigDecimal actualAmount = totalsBySource.get(category.getId()) == null ? BigDecimal.ZERO : totalsBySource.get(category.getId());

			if (budgetedAmount.compareTo(BigDecimal.ZERO) != 0 || actualAmount.compareTo(BigDecimal.ZERO) != 0) {
				final Map<String, Object> object = new LinkedHashMap<>();
				object.put("source", CryptoUtil.decryptWrapper(category.getName(), user));

				final BigDecimal daysInPeriod = new BigDecimal(CategoryPeriods.valueOf(category.getPeriodType()).getDaysInPeriod(dates[0]));
				final BigDecimal averageAmount = actualAmount.divide(new BigDecimal(totalDaysInRange), RoundingMode.HALF_UP).multiply(daysInPeriod);
				object.put("average", FormatUtil.formatCurrency(averageAmount, user));
				object.put("averageStyle", (FormatUtil.isRed(category, averageAmount) ? FormatUtil.formatRed() : ""));

				final BigDecimal averageBudgeted = budgetedAmount.divide(new BigDecimal(totalDaysInRange), RoundingMode.HALF_UP).multiply(daysInPeriod);
				object.put("averageBudgeted", FormatUtil.formatCurrency(averageBudgeted, user));
				object.put("averageBudgetedStyle", (FormatUtil.isRed(category, averageBudgeted) ? FormatUtil.formatRed() : ""));

				final BigDecimal diff = (averageAmount.subtract(averageBudgeted));
				object.put("difference", FormatUtil.formatCurrency(diff, user));
				object.put("differenceStyle", (FormatUtil.isRed(category, diff) ? FormatUtil.formatRed() : ""));

				object.put("period", LocaleUtil.getTranslation(user).getString("BUDGET_CATEGORY_TYPE_" + category.getPeriodType()));
				object.put("periodStyle", (FormatUtil.isRed(category, actualAmount) ? FormatUtil.formatRed() : ""));

				totalActualByPeriod.put(category.getPeriodType(), (totalActualByPeriod.get(category.getPeriodType()) == null ? BigDecimal.ZERO : totalActualByPeriod.get(category.getPeriodType())).add(averageAmount));
				totalBudgetedByPeriod.put(category.getPeriodType(), (totalBudgetedByPeriod.get(category.getPeriodType()) == null ? BigDecimal.ZERO : totalBudgetedByPeriod.get(category.getPeriodType())).add(averageBudgeted));

				data.add(object);
			}
		}

		for (String period : totalActualByPeriod.keySet()) {
			final Map<String, Object> object = new LinkedHashMap<>();
			object.put("source", LocaleUtil.getTranslation(user).getString(income ? "AVERAGE_INCOME" : "AVERAGE_EXPENSES") + " / " + LocaleUtil.getTranslation(user).getString("BUDGET_CATEGORY_TYPE_" + period));
			object.put("sourceStyle", "font-weight: bold;");
			object.put("average", FormatUtil.formatCurrency(totalActualByPeriod.get(period), user));
			object.put("averageStyle", "font-weight: bold;" + (totalActualByPeriod.get(period).compareTo(BigDecimal.ZERO) >= 0 ? FormatUtil.formatRed() : ""));
			object.put("averageBudgeted", FormatUtil.formatCurrency(totalBudgetedByPeriod.get(period), user));
			object.put("averageBudgetedStyle", "font-weight: bold;" + (totalBudgetedByPeriod.get(period).compareTo(BigDecimal.ZERO) >= 0 ? FormatUtil.formatRed() : ""));
			final BigDecimal diff = (totalActualByPeriod.get(period).subtract(totalBudgetedByPeriod.get(period)));
			object.put("difference", FormatUtil.formatCurrency(diff, user));
			object.put("differenceStyle", "font-weight: bold;" + (FormatUtil.isRed(diff) ? FormatUtil.formatRed() : ""));
			object.put("period", LocaleUtil.getTranslation(user).getString("BUDGET_CATEGORY_TYPE_" + period));
			object.put("periodStyle", "font-weight: bold;" + FormatUtil.formatRed());
			data.add(object);
		}
	}

	@GetMapping("/inflowandoutflowbyaccount")
	public ReportDataResponseDto inflowAndOutflowByAccount(@AuthenticationPrincipal final User user,
			@RequestParam final String interval,
			@RequestParam(required = false) final String startDate,
			@RequestParam(required = false) final String endDate) {
		try {
			final Date[] dates = processInterval(interval, startDate, endDate);
			final List<Transaction> txns = transactions.selectTransactions(user, dates[0], dates[1]);

			final List<Map<String, Object>> data = new ArrayList<>();
			calculateInflowOutflowByAccount(data, user, sources.selectAccounts(user), txns);

			return reportResponseConverter.convert(data);
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	private void calculateInflowOutflowByAccount(final List<Map<String, Object>> data, final User user, final List<Account> accounts, final List<Transaction> txns) throws CryptoException {
		Collections.sort(accounts, new Comparator<Account>() {
			@Override
			public int compare(final Account o1, final Account o2) {
				if (o1 == null || o2 == null) return 0;
				if (o1.isDebit() != o2.isDebit()) return o1.isDebit() ? -1 : 1;
				try {
					return CryptoUtil.decryptWrapper(o1.getName(), user).compareTo(CryptoUtil.decryptWrapper(o2.getName(), user));
				}
				catch (CryptoException e) {
					return 0;
				}
			}
		});

		final Map<Integer, BigDecimal> totalInflowsBySource = new HashMap<>();
		final Map<Integer, BigDecimal> totalOutflowsBySource = new HashMap<>();
		final Map<Integer, List<Transaction>> transactionsBySource = new HashMap<>();

		for (Transaction transaction : txns) {
			for (Split split : transaction.getSplits()) {
				if ("D".equals(split.getFromType()) || "C".equals(split.getFromType())) {
					final int source = split.getFromSource();
					final Transaction t = new Transaction();
					t.setDate(transaction.getDate());
					t.setDescription(transaction.getDescription());
					t.setNumber(transaction.getNumber());
					t.setSplits(new ArrayList<>());
					t.getSplits().add(split);
					if (transactionsBySource.get(source) == null) transactionsBySource.put(source, new ArrayList<>());
					transactionsBySource.get(source).add(t);
					final BigDecimal splitAmount = CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true);
					if (splitAmount.compareTo(BigDecimal.ZERO) < 0) {
						totalInflowsBySource.put(source, splitAmount.negate().add(totalInflowsBySource.get(source) == null ? BigDecimal.ZERO : totalInflowsBySource.get(source)));
					}
					else {
						totalOutflowsBySource.put(source, splitAmount.add(totalOutflowsBySource.get(source) == null ? BigDecimal.ZERO : totalOutflowsBySource.get(source)));
					}
				}
				if ("D".equals(split.getToType()) || "C".equals(split.getToType())) {
					final int source = split.getToSource();
					final Transaction t = new Transaction();
					t.setDate(transaction.getDate());
					t.setDescription(transaction.getDescription());
					t.setNumber(transaction.getNumber());
					t.setSplits(new ArrayList<>());
					t.getSplits().add(split);
					if (transactionsBySource.get(source) == null) transactionsBySource.put(source, new ArrayList<>());
					transactionsBySource.get(source).add(t);
					final BigDecimal splitAmount = CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true);
					if (splitAmount.compareTo(BigDecimal.ZERO) < 0) {
						totalOutflowsBySource.put(source, splitAmount.negate().add(totalOutflowsBySource.get(source) == null ? BigDecimal.ZERO : totalOutflowsBySource.get(source)));
					}
					else {
						totalInflowsBySource.put(source, splitAmount.add(totalInflowsBySource.get(source) == null ? BigDecimal.ZERO : totalInflowsBySource.get(source)));
					}
				}
			}
		}

		for (Account account : accounts) {
			final BigDecimal inflow = totalInflowsBySource.get(account.getId()) == null ? BigDecimal.ZERO : totalInflowsBySource.get(account.getId());
			final BigDecimal outflow = totalOutflowsBySource.get(account.getId()) == null ? BigDecimal.ZERO : totalOutflowsBySource.get(account.getId());

			if (inflow.compareTo(BigDecimal.ZERO) != 0 || outflow.compareTo(BigDecimal.ZERO) != 0) {
				final Map<String, Object> object = new LinkedHashMap<>();
				object.put("source", CryptoUtil.decryptWrapper(account.getName(), user));
				object.put("sourceStyle", (account.isDebit() ? "" : FormatUtil.formatRed()));
				object.put("inflow", FormatUtil.formatCurrency(inflow, user));
				object.put("inflowStyle", (FormatUtil.isRed(account, inflow) ? FormatUtil.formatRed() : ""));
				object.put("outflow", FormatUtil.formatCurrency(outflow, user));
				object.put("outflowStyle", (FormatUtil.isRed(account, outflow.negate()) ? FormatUtil.formatRed() : ""));
				final BigDecimal diff = (inflow.subtract(outflow));
				object.put("difference", FormatUtil.formatCurrency(diff, user));
				object.put("differenceStyle", (FormatUtil.isRed(account, diff) ? FormatUtil.formatRed() : ""));

				final List<Transaction> transactionsInCategory = transactionsBySource.get(account.getId());
				if (transactionsInCategory != null) {
					Collections.sort(transactionsInCategory, new Comparator<Transaction>() {
						@Override
						public int compare(final Transaction o1, final Transaction o2) {
							if (o1 == null || o2 == null) return 0;
							return o1.getDate().compareTo(o2.getDate());
						}
					});
					final List<Map<String, Object>> ts = new ArrayList<>();
					for (Transaction t : transactionsInCategory) {
						final Map<String, Object> o = new LinkedHashMap<>();
						o.put("date", FormatUtil.formatDate(t.getDate(), user));
						o.put("description", CryptoUtil.decryptWrapper(t.getDescription(), user));
						o.put("number", CryptoUtil.decryptWrapper(t.getNumber(), user));
						final Split s = t.getSplits().get(0);
						o.put("from", CryptoUtil.decryptWrapper(s.getFromSourceName(), user));
						o.put("to", CryptoUtil.decryptWrapper(s.getToSourceName(), user));
						final BigDecimal splitAmount = CryptoUtil.decryptWrapperBigDecimal(s.getAmount(), user, true);
						o.put("amount", FormatUtil.formatCurrency(splitAmount, user));
						o.put("amountStyle", (FormatUtil.isRed(account, splitAmount) ? FormatUtil.formatRed() : ""));
						ts.add(o);
					}
					object.put("transactions", ts);
				}
				data.add(object);
			}
		}
	}

	@GetMapping("/inflowandoutflowbypayee")
	public ReportDataResponseDto inflowAndOutflowByPayee(@AuthenticationPrincipal final User user,
			@RequestParam final String interval,
			@RequestParam(required = false) final String startDate,
			@RequestParam(required = false) final String endDate) {
		try {
			final Date[] dates = processInterval(interval, startDate, endDate);
			final List<Transaction> txns = transactions.selectTransactions(user, dates[0], dates[1]);

			final List<Map<String, Object>> data = new ArrayList<>();
			calculateInflowOutflowByPayee(data, user, txns);

			return reportResponseConverter.convert(data);
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	private void calculateInflowOutflowByPayee(final List<Map<String, Object>> data, final User user, final List<Transaction> txns) throws CryptoException {
		final Map<String, BigDecimal> totalInflowsByPayee = new HashMap<>();
		final Map<String, BigDecimal> totalOutflowsByPayee = new HashMap<>();
		final Map<String, List<Transaction>> transactionsByPayee = new HashMap<>();

		for (Transaction transaction : txns) {
			for (Split split : transaction.getSplits()) {
				final String payee = CryptoUtil.decryptWrapper(transaction.getDescription(), user);
				final Transaction t = new Transaction();
				t.setDate(transaction.getDate());
				t.setDescription(transaction.getDescription());
				t.setNumber(transaction.getNumber());
				t.setSplits(new ArrayList<>());
				t.getSplits().add(split);
				if (transactionsByPayee.get(payee) == null) transactionsByPayee.put(payee, new ArrayList<>());
				transactionsByPayee.get(payee).add(t);
				final BigDecimal splitAmount = CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true);
				if (splitAmount.compareTo(BigDecimal.ZERO) < 0) {
					totalInflowsByPayee.put(payee, splitAmount.negate().add(totalInflowsByPayee.get(payee) == null ? BigDecimal.ZERO : totalInflowsByPayee.get(payee)));
				}
				else {
					totalOutflowsByPayee.put(payee, splitAmount.add(totalOutflowsByPayee.get(payee) == null ? BigDecimal.ZERO : totalOutflowsByPayee.get(payee)));
				}
			}
		}

		final List<String> payees = new ArrayList<>(transactionsByPayee.keySet());
		Collections.sort(payees);

		for (String payee : payees) {
			final BigDecimal inflow = totalInflowsByPayee.get(payee) == null ? BigDecimal.ZERO : totalInflowsByPayee.get(payee);
			final BigDecimal outflow = totalOutflowsByPayee.get(payee) == null ? BigDecimal.ZERO : totalOutflowsByPayee.get(payee);

			if (inflow.compareTo(BigDecimal.ZERO) != 0 || outflow.compareTo(BigDecimal.ZERO) != 0) {
				final Map<String, Object> object = new LinkedHashMap<>();
				object.put("source", payee);
				object.put("inflow", FormatUtil.formatCurrency(inflow, user));
				object.put("inflowStyle", (FormatUtil.isRed(inflow) ? FormatUtil.formatRed() : ""));
				object.put("outflow", FormatUtil.formatCurrency(outflow, user));
				object.put("outflowStyle", (FormatUtil.isRed(outflow.negate()) ? FormatUtil.formatRed() : ""));
				final BigDecimal diff = (inflow.subtract(outflow));
				object.put("difference", FormatUtil.formatCurrency(diff, user));
				object.put("differenceStyle", (FormatUtil.isRed(diff) ? FormatUtil.formatRed() : ""));

				final List<Transaction> transactionsForPayee = transactionsByPayee.get(payee);
				if (transactionsForPayee != null) {
					Collections.sort(transactionsForPayee, new Comparator<Transaction>() {
						@Override
						public int compare(final Transaction o1, final Transaction o2) {
							if (o1 == null || o2 == null) return 0;
							return o1.getDate().compareTo(o2.getDate());
						}
					});
					final List<Map<String, Object>> ts = new ArrayList<>();
					for (Transaction t : transactionsForPayee) {
						final Map<String, Object> o = new LinkedHashMap<>();
						o.put("date", FormatUtil.formatDate(t.getDate(), user));
						o.put("description", CryptoUtil.decryptWrapper(t.getDescription(), user));
						o.put("number", CryptoUtil.decryptWrapper(t.getNumber(), user));
						final Split s = t.getSplits().get(0);
						o.put("from", CryptoUtil.decryptWrapper(s.getFromSourceName(), user));
						o.put("to", CryptoUtil.decryptWrapper(s.getToSourceName(), user));
						final BigDecimal splitAmount = CryptoUtil.decryptWrapperBigDecimal(s.getAmount(), user, true);
						o.put("amount", FormatUtil.formatCurrency(splitAmount, user));
						o.put("amountStyle", (FormatUtil.isRed(splitAmount) ? FormatUtil.formatRed() : ""));
						ts.add(o);
					}
					object.put("transactions", ts);
				}
				data.add(object);
			}
		}
	}

	@GetMapping("/balancesovertime")
	public ReportDataResponseDto balancesOverTime(@AuthenticationPrincipal final User user,
			@RequestParam final String interval,
			@RequestParam(required = false, defaultValue = "false") final boolean netWorthOnly,
			@RequestParam(required = false) final String startDate,
			@RequestParam(required = false) final String endDate) {
		try {
			final Date[] dates = processInterval(interval, startDate, endDate);
			final List<Account> accountBalances = sources.selectAccountBalances(user);
			final Map<Integer, BigDecimal> balances = new HashMap<>();

			if (accountBalances.get(0).getStartDate().after(dates[0])) dates[0] = accountBalances.get(0).getStartDate();
			int numberOfDaysBetween = DateUtil.getDaysBetween(dates[0], dates[1], false);
			int daysBetweenReport = Math.max(1, numberOfDaysBetween / 500);

			final List<Map<String, Object>> data = new ArrayList<>();
			int accountBalancesIndex = 0;

			while (dates[0].before(dates[1])) {
				while (accountBalancesIndex < accountBalances.size()
						&& accountBalances.get(accountBalancesIndex) != null
						&& accountBalances.get(accountBalancesIndex).getStartDate() != null
						&& accountBalances.get(accountBalancesIndex).getStartDate().before(dates[0])) {
					final BigDecimal balance = CryptoUtil.decryptWrapperBigDecimal(accountBalances.get(accountBalancesIndex).getBalance(), user, true);
					balances.put(accountBalances.get(accountBalancesIndex).getId(), balance);
					accountBalancesIndex++;
				}

				final Map<String, Object> dataItem = new LinkedHashMap<>();
				dataItem.put("date", FormatUtil.formatDate(dates[0], user));
				BigDecimal netWorth = BigDecimal.ZERO;
				for (int j : balances.keySet()) {
					if (!netWorthOnly) dataItem.put("a" + j, balances.get(j));
					netWorth = netWorth.add(balances.get(j));
				}
				dates[0] = DateUtil.addDays(dates[0], daysBetweenReport);
				dataItem.put("netWorth", netWorth);
				data.add(dataItem);
			}

			return reportResponseConverter.convert(data);
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}