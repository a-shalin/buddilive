package ca.digitalcave.buddi.live.controller;

import ca.digitalcave.buddi.live.api.converter.ScheduledTransactionsResponseConverter;
import ca.digitalcave.buddi.live.api.dto.ScheduledTransactionsExecuteResponseDto;
import ca.digitalcave.buddi.live.api.dto.ScheduledTransactionsResponseDto;
import ca.digitalcave.buddi.live.api.dto.SuccessResponseDto;
import ca.digitalcave.buddi.live.api.dto.request.ScheduledTransactionRequestDto;
import ca.digitalcave.buddi.live.service.ScheduledTransactionsTransactionalService;
import ca.digitalcave.buddi.live.db.ScheduledTransactions;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.ScheduledTransaction;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.common.DateUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/data/scheduledtransactions")
public class ScheduledTransactionsController {

	@Autowired
	private ScheduledTransactions scheduledTransactions;

	@Autowired
	private ScheduledTransactionsTransactionalService scheduledTransactionsTransactionalService;

	@Autowired
	private ScheduledTransactionsResponseConverter scheduledTransactionsResponseConverter;

	@GetMapping
	public ScheduledTransactionsResponseDto get(@AuthenticationPrincipal final User user) {
		try {
			final List<ScheduledTransaction> list = scheduledTransactions.selectScheduledTransactions(user);
			return scheduledTransactionsResponseConverter.convert(user, list);
		}
		catch (final CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	@PostMapping
	public SuccessResponseDto post(@AuthenticationPrincipal final User user, @RequestBody final ScheduledTransactionRequestDto request) {
		try {
			final Action action = request.action();
			switch (action) {
				case INSERT -> scheduledTransactionsTransactionalService.insertScheduledTransaction(user, ScheduledTransaction.fromDto(request));
				case UPDATE -> scheduledTransactionsTransactionalService.updateScheduledTransaction(user, ScheduledTransaction.fromDto(request));
				case DELETE -> scheduledTransactionsTransactionalService.deleteScheduledTransaction(user, request.id());
				default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
			}
			return new SuccessResponseDto(true);
		}
		catch (final DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
		catch (final CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	@PostMapping("/execute")
	public ScheduledTransactionsExecuteResponseDto execute(@AuthenticationPrincipal final User user, @RequestBody final String body) {
		try {
			Date userDate = null;
			try {
				userDate = FormatUtil.parseDateInternal(body);
			}
			catch (final Throwable e) {
				;
			}
			if (userDate == null || Math.abs(DateUtil.getDaysBetween(new Date(), userDate, false)) > 2) {
				userDate = new Date();
			}

			final String messages = scheduledTransactionsTransactionalService.execute(user, userDate);
			return scheduledTransactionsResponseConverter.convertExecute(messages);
		}
		catch (final CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
		catch (final DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}
