package ca.digitalcave.buddi.live.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.digitalcave.buddi.live.api.converter.StoreResponseConverter;
import ca.digitalcave.buddi.live.api.dto.StoreResponseDto;
import ca.digitalcave.buddi.live.model.User;

@RestController
@RequestMapping("/stores")
public class StoreController {

	@Autowired
	private StoreResponseConverter storeResponseConverter;

	@GetMapping("/currencies")
	public StoreResponseDto currencies() {
		return storeResponseConverter.convertCurrencies();
	}

	@GetMapping("/locales")
	public StoreResponseDto locales(@AuthenticationPrincipal final User user) {
		return storeResponseConverter.convertLocales(user);
	}
}
