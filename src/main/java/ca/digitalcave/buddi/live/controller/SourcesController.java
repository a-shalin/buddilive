package ca.digitalcave.buddi.live.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ca.digitalcave.buddi.live.api.converter.SourcesResponseConverter;
import ca.digitalcave.buddi.live.api.dto.SourcesResponseDto;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.model.AccountType;
import ca.digitalcave.buddi.live.model.Category;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

@RestController
@RequestMapping("/data/sources")
public class SourcesController {

	@Autowired
	private Sources sources;

	@Autowired
	private SourcesResponseConverter sourcesResponseConverter;

	@GetMapping("/{direction}")
	public SourcesResponseDto get(@AuthenticationPrincipal final User user, @PathVariable final String direction) {
		try {
			final boolean isIncome = "from".equals(direction);
			final List<AccountType> accountsByType = sources.selectAccountTypes(user);
			final List<Category> categories = Category.getHierarchy(sources.selectCategories(user, isIncome));
			return sourcesResponseConverter.convert(user, accountsByType, categories);
		}
		catch (final CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}
