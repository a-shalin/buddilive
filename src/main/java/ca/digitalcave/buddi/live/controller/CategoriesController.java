package ca.digitalcave.buddi.live.controller;

import ca.digitalcave.buddi.live.api.converter.CategoriesResponseConverter;
import ca.digitalcave.buddi.live.api.dto.CategoriesMutationResponseDto;
import ca.digitalcave.buddi.live.api.dto.CategoriesResponseDto;
import ca.digitalcave.buddi.live.api.dto.CategoriesResponseDto.CategoryNodeDto;
import ca.digitalcave.buddi.live.api.dto.request.CategoriesRequestDto;
import ca.digitalcave.buddi.live.service.CategoriesTransactionalService;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.*;
import ca.digitalcave.buddi.live.model.CategoryPeriod.CategoryPeriods;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/data/categories")
public class CategoriesController {

	@Autowired
	private Sources sources;

	@Autowired
	private Transactions transactions;

	@Autowired
	private CategoriesResponseConverter categoriesResponseConverter;

	@Autowired
	private CategoriesTransactionalService categoriesTransactionalService;

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
	public CategoriesMutationResponseDto post(@AuthenticationPrincipal final User user, @RequestBody final CategoriesRequestDto request) {
		try {
			final Action action = request.action();
			if (action != Action.INSERT
					&& action != Action.DELETE
					&& action != Action.UNDELETE
					&& action != Action.UPDATE
					&& action != Action.COPY_FROM_PREVIOUS
					&& action != Action.SET) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
			}

			CategoryNodeDto data = null;

			categoriesTransactionalService.applyAction(user, request);
			if (Action.SET == action) {
				final CategoryPeriod cp = new CategoryPeriod(
						CategoryPeriods.valueOf(request.periodType()),
						FormatUtil.parseDateInternal(request.date()),
						Integer.parseInt(request.offset() != null ? request.offset() : "0"));

				final Category c = sources.selectCategory(user, cp, request.categoryId());
				final List<Transaction> txns = transactions.selectTransactions(user, c, cp.getCurrentPeriodStartDate(), cp.getCurrentPeriodEndDate());
				data = categoriesResponseConverter.convertCategoryNode(c, cp, txns, user);
			}
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
