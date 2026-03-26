package ca.digitalcave.buddi.live.controller;

import java.util.List;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ca.digitalcave.buddi.live.api.converter.AccountsResponseConverter;
import ca.digitalcave.buddi.live.api.dto.AccountsResponseDto;
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
	public String post(@AuthenticationPrincipal User user, @RequestBody String body) {
		try {
			final JSONObject request = new JSONObject(body);
			final String action = request.optString("action");
			final Account account = new Account(request);

			if ("insert".equals(action)) {
				ConstraintsChecker.checkInsertAccount(account, user, sources, crypto);
				final int count = sources.insertAccount(user, account);
				if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
			}
			else if ("delete".equals(action) || "undelete".equals(action)) {
				if (sources.selectSourceAssociatedCount(user, account) == 0) {
					final int count = sources.deleteSource(user, account);
					if (count != 1) throw new DatabaseException(String.format("Delete failed; expected 1 row, returned %s", count));
				}
				else {
					account.setDeleted("delete".equals(action));
					final int count = sources.updateSourceDeleted(user, account);
					if (count != 1) throw new DatabaseException(String.format("Delete / undelete failed; expected 1 row, returned %s", count));
				}
			}
			else if ("update".equals(action)) {
				ConstraintsChecker.checkUpdateAccount(account, user, sources, crypto);
				final int count = sources.updateAccount(user, account);
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
