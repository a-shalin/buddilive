package ca.digitalcave.buddi.live.controller;

import ca.digitalcave.buddi.live.api.converter.AccountsResponseConverter;
import ca.digitalcave.buddi.live.api.dto.AccountsResponseDto;
import ca.digitalcave.buddi.live.api.dto.SuccessResponseDto;
import ca.digitalcave.buddi.live.api.dto.request.AccountRequestDto;
import ca.digitalcave.buddi.live.service.AccountsTransactionalService;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.Account;
import ca.digitalcave.buddi.live.model.AccountType;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/data/accounts")
public class AccountsController {

	@Autowired
	private Sources sources;

	@Autowired
	private AccountsTransactionalService accountsTransactionalService;

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
	public SuccessResponseDto post(@AuthenticationPrincipal final User user, @RequestBody final AccountRequestDto accountRequestDto) {
		try {
			final Action action = accountRequestDto.action();
			final Account account = Account.fromDto(accountRequestDto);

			if (action != Action.INSERT && action != Action.DELETE && action != Action.UNDELETE && action != Action.UPDATE) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
			}

			accountsTransactionalService.applyAction(user, action, account);

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
