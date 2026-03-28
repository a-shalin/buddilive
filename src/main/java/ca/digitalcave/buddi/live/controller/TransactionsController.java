package ca.digitalcave.buddi.live.controller;

import ca.digitalcave.buddi.live.api.dto.SuccessResponseDto;
import ca.digitalcave.buddi.live.api.dto.request.TransactionRequestDto;
import ca.digitalcave.buddi.live.service.TransactionsTransactionalService;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.Source;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.Transaction;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.math.BigDecimal;

@RestController
@RequestMapping("/data/transactions")
public class TransactionsController {

	@Autowired
	private Sources sources;

	@Autowired
	private Transactions transactions;

	@Autowired
	private TransactionsTransactionalService transactionsTransactionalService;

	@Autowired
	private JsonFactory jsonFactory;

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public StreamingResponseBody get(@AuthenticationPrincipal final User user,
			@RequestParam final int source,
			@RequestParam final int start,
			@RequestParam final int limit,
			@RequestParam(required = false) final String search) {
		final Source src = sources.selectSource(user, source);
		final String searchLower = (search != null ? search.toLowerCase(user.getLocale()) : null);

		return outputStream -> {
			try (JsonGenerator generator = jsonFactory.createGenerator(outputStream)) {
				generator.writeStartObject();
				generator.writeBooleanField("success", true);
				generator.writeArrayFieldStart("data");
				final MutableInt total = new MutableInt(0);
				final MutableInt count = new MutableInt(0);
				transactions.selectTransactions(user, src, new ResultHandler<Transaction>() {
					public void handleResult(final ResultContext<? extends Transaction> context) {
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
		};
	}

	@PostMapping
	public SuccessResponseDto post(@AuthenticationPrincipal final User user, @RequestBody final TransactionRequestDto request) {
		try {
			final Action action = request.action();
			if (action != Action.INSERT && action != Action.UPDATE && action != Action.DELETE) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
			}

			final Transaction transaction = (action == Action.INSERT || action == Action.UPDATE) ? Transaction.fromDto(request) : null;
			transactionsTransactionalService.applyAction(user, action, transaction, request.id());

			return new SuccessResponseDto(true);
		}
		catch (DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}
