package ca.digitalcave.buddi.live.controller;

import ca.digitalcave.buddi.live.api.converter.AccountsResponseConverter;
import ca.digitalcave.buddi.live.api.dto.AccountsResponseDto;
import ca.digitalcave.buddi.live.api.dto.SuccessResponseDto;
import ca.digitalcave.buddi.live.api.dto.request.AccountRequestDto;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.Account;
import ca.digitalcave.buddi.live.model.AccountType;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/data/accounts")
public class AccountsController {

	@Autowired
	private Sources sources;

	@Autowired
	private Transactions transactions;

	@Autowired
	private Crypto crypto;

	@Autowired
	private AccountsResponseConverter accountsResponseConverter;

	@GetMapping
	public AccountsResponseDto get(@AuthenticationPrincipal final User user) {
		try {
			final List<AccountType> accountsByType = sources.selectAccountTypes(user);
			return accountsResponseConverter.convert(user, accountsByType);
		}
		catch (final CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	@PostMapping
	@Transactional
	public SuccessResponseDto post(@AuthenticationPrincipal User user, @RequestBody final AccountRequestDto request) {
		try {
			final Action action = request.action();
			final Account account = Account.fromDto(request);

			if (Action.INSERT == action) {
				ConstraintsChecker.checkInsertAccount(account, user, sources, crypto);
				final int count = sources.insertAccount(user, account);
				if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
			}
			else if (Action.DELETE == action || Action.UNDELETE == action) {
				if (sources.selectSourceAssociatedCount(user, account) == 0) {
					final int count = sources.deleteSource(user, account);
					if (count != 1) throw new DatabaseException(String.format("Delete failed; expected 1 row, returned %s", count));
				}
				else {
					account.setDeleted(Action.DELETE == action);
					final int count = sources.updateSourceDeleted(user, account);
					if (count != 1) throw new DatabaseException(String.format("Delete / undelete failed; expected 1 row, returned %s", count));
				}
			}
			else if (Action.UPDATE == action) {
				ConstraintsChecker.checkUpdateAccount(account, user, sources, crypto);
				final int count = sources.updateAccount(user, account);
				if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
			}
			else {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
			}

			DataUpdater.updateBalances(user, sources, transactions, crypto);

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
