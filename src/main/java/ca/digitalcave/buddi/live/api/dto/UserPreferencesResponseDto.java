package ca.digitalcave.buddi.live.api.dto;

public record UserPreferencesResponseDto(
		boolean encrypt,
		boolean storeEmail,
		String locale,
		String currency,
		String dateFormat,
		boolean currencyAfter,
		String decimalSeparator,
		String thousandSeparator,
		String negativeFormat,
		boolean showCurrencySymbol,
		boolean currencySpacing,
		boolean useTwoFactor,
		boolean showDeleted,
		boolean skipFocusOnTransactionNumber,
		boolean success) {
}
