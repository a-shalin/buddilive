package ca.digitalcave.buddi.live.api.converter;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import ca.digitalcave.buddi.live.api.dto.UserPreferencesResponseDto;
import ca.digitalcave.buddi.live.model.User;

@Component
public class UserPreferencesResponseConverter {

	public UserPreferencesResponseDto convert(final User user) {
		return new UserPreferencesResponseDto(
				user.isEncrypted(),
				StringUtils.isNotBlank(user.getEmail()),
				user.getLocale().toString(),
				user.getCurrency().getCurrencyCode(),
				user.getOverrideDateFormat(),
				user.isCurrencyAfter(),
				user.getOverrideDecimalSeparator(),
				user.getOverrideThousandsSeparator(),
				user.getNegativeFormat(),
				user.isShowCurrencySymbol(),
				user.useCurrencySpacing(),
				user.isTwoFactorRequired(),
				user.isShowDeleted(),
				true);
	}
}
