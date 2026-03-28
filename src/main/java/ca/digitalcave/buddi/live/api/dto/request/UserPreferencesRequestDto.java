package ca.digitalcave.buddi.live.api.dto.request;

import ca.digitalcave.buddi.live.controller.Action;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserPreferencesRequestDto(
		Action action,
		Boolean encrypt,
		String encryptPassword,
		Boolean storeEmail,
		String locale,
		String currency,
		String dateFormat,
		Boolean currencyAfter,
		String decimalSeparator,
		String thousandSeparator,
		String negativeFormat,
		Boolean showCurrencySymbol,
		Boolean currencySpacing,
		Boolean useTwoFactor,
		Boolean showDeleted,
		Boolean skipFocusOnTransactionNumber) {
}
