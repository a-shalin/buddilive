package ca.digitalcave.buddi.live.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ca.digitalcave.buddi.live.api.converter.ParentsResponseConverter;
import ca.digitalcave.buddi.live.api.dto.ParentsResponseDto;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.model.Category;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

@RestController
@RequestMapping("/data/categories/parents")
public class ParentsController {

	@Autowired
	private Sources sources;

	@Autowired
	private ParentsResponseConverter parentsResponseConverter;

	@GetMapping
	public ParentsResponseDto get(@AuthenticationPrincipal final User user,
			@RequestParam(required = false) final Integer exclude) {
		try {
			final List<Category> categories = Category.getHierarchy(sources.selectCategories(user));
			final Category category = (exclude == null ? null : sources.selectCategory(user, exclude));
			return parentsResponseConverter.convert(user, categories, category);
		}
		catch (final CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}
