package ca.digitalcave.buddi.live.controller;

import java.math.BigDecimal;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.Source;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.Transaction;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

@RestController
@RequestMapping("/data/transactions")
public class TransactionsController {

	@Autowired
	private Sources sources;

	@Autowired
	private Transactions transactions;

	@Autowired
	private Crypto crypto;

	@Autowired
	private JsonFactory jsonFactory;

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public StreamingResponseBody get(@AuthenticationPrincipal User user,
			@RequestParam int source,
			@RequestParam int start,
			@RequestParam int limit,
			@RequestParam(required = false) String search) {
		final Source src = sources.selectSource(user, source);
		final String searchLower = (search != null ? search.toLowerCase(user.getLocale()) : null);

		return outputStream -> {
			final JsonGenerator generator = jsonFactory.createGenerator(outputStream);
			try {
				generator.writeStartObject();
				generator.writeBooleanField("success", true);
				generator.writeArrayFieldStart("data");
				final MutableInt total = new MutableInt(0);
				final MutableInt count = new MutableInt(0);
				transactions.selectTransactions(user, src, new ResultHandler<Transaction>() {
					public void handleResult(ResultContext<? extends Transaction> context) {
						final Transaction t = context.getResultObject();
						try {
							final String description = CryptoUtil.decryptWrapper(t.getDescription(), user);
							final String number = CryptoUtil.decryptWrapper(t.getNumber(), user);
							if (searchLower != null) {
								if (!description.toLowerCase(user.getLocale()).contains(searchLower)
										&& !number.toLowerCase(user.getLocale()).contains(searchLower)) {
									boolean match = false;
									for (Split s : t.getSplits()) {
										if (CryptoUtil.decryptWrapper(s.getMemo(), user).toLowerCase(user.getLocale()).contains(searchLower)) {
											match = true;
											break;
										}
									}
									if (!match) return;
								}
							}

							total.increment();
							if (context.getResultCount() < start || count.intValue() >= limit) return;
							count.increment();

							generator.writeStartObject();
							generator.writeNumberField("id", t.getId());
							generator.writeStringField("date", FormatUtil.formatDate(t.getDate(), user));
							generator.writeStringField("dateIso", FormatUtil.formatDateInternal(t.getDate()));
							generator.writeStringField("description", description);
							generator.writeStringField("number", number);
							generator.writeBooleanField("deleted", t.isDeleted());
							generator.writeArrayFieldStart("splits");
							for (Split s : t.getSplits()) {
								generator.writeStartObject();
								generator.writeNumberField("id", s.getId());
								final BigDecimal amount = CryptoUtil.decryptWrapperBigDecimal(s.getAmount(), user, false);
								generator.writeStringField("amount", FormatUtil.formatCurrency(amount, user));
								generator.writeNumberField("amountNumber", amount);
								generator.writeBooleanField("amountInDebitColumn", s.isDebit(src));
								generator.writeStringField("amountStyle", (FormatUtil.isRed(src, user, s) ? FormatUtil.formatRed() : ""));
								generator.writeNumberField("fromId", s.getFromSource());
								generator.writeStringField("from", CryptoUtil.decryptWrapper(s.getFromSourceName(), user));
								generator.writeNumberField("toId", s.getToSource());
								generator.writeStringField("to", CryptoUtil.decryptWrapper(s.getToSourceName(), user));
								generator.writeBooleanField("debit", s.isDebit(src));
								final BigDecimal balance = CryptoUtil.decryptWrapperBigDecimal(s.getFromSource() == src.getId() ? s.getFromBalance() : s.getToBalance(), user, true);
								generator.writeStringField("balance", FormatUtil.formatCurrency(balance, user, src));
								generator.writeStringField("balanceStyle", (FormatUtil.isRed(src, balance) ? FormatUtil.formatRed() : ""));
								generator.writeStringField("memo", CryptoUtil.decryptWrapper(s.getMemo(), user));
								generator.writeEndObject();
							}
							generator.writeEndArray();
							generator.writeEndObject();
						}
						catch (Exception e) {
							throw new RuntimeException(e);
						}
					}
				});
				generator.writeEndArray();
				generator.writeNumberField("total", total.intValue());
				generator.writeEndObject();
				generator.flush();
			}
			finally {
				generator.close();
			}
		};
	}

	@PostMapping
	@Transactional
	public String post(@AuthenticationPrincipal User user, @RequestBody String body) {
		try {
			final JSONObject json = new JSONObject(body);
			final String action = json.optString("action");

			if ("insert".equals(action)) {
				final Transaction transaction = new Transaction(json);
				ConstraintsChecker.checkInsertTransaction(transaction, user, sources, crypto);

				int count = transactions.insertTransaction(user, transaction);
				if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));

				for (Split split : transaction.getSplits()) {
					split.setTransactionId(transaction.getId());
					count = transactions.insertSplit(user, split);
					if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
				}
			}
			else if ("update".equals(action)) {
				final Transaction transaction = new Transaction(json);
				ConstraintsChecker.checkUpdateTransaction(transaction, user, sources, crypto);

				int count = transactions.updateTransaction(user, transaction);
				if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));

				count = transactions.deleteSplits(user, transaction);
				if (count == 0) throw new DatabaseException("Failed to delete splits; expected 1 or more rows, returned 0");

				for (Split split : transaction.getSplits()) {
					split.setTransactionId(transaction.getId());
					count = transactions.insertSplit(user, split);
					if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
				}
			}
			else if ("delete".equals(action)) {
				final Transaction transaction = new Transaction();
				transaction.setId(json.getLong("id"));
				int count = transactions.deleteTransaction(user, transaction);
				if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
			}
			else {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
			}

			DataUpdater.updateBalances(user, sources, transactions, crypto);

			final JSONObject result = new JSONObject();
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