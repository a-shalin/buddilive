package ca.digitalcave.buddi.live.util;

import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;

import ca.digitalcave.buddi.live.model.User;

public class LocaleUtil {

	public static ResourceBundle getTranslation() {
		return ResourceBundle.getBundle("i18n");
	}

	public static ResourceBundle getTranslation(User user) {
		if (user != null && user.getLocale() != null) {
			return ResourceBundle.getBundle("i18n", user.getLocale());
		}
		return ResourceBundle.getBundle("i18n");
	}

	public static ResourceBundle getTranslation(Locale locale) {
		if (locale != null) {
			return ResourceBundle.getBundle("i18n", locale);
		}
		return ResourceBundle.getBundle("i18n");
	}

	public static Locale parseLocale(String rawLocale, Locale defaultLocale) {
		if (StringUtils.isBlank(rawLocale)) return defaultLocale;

		try {
			return LocaleUtils.toLocale(rawLocale);
		}
		catch (IllegalArgumentException e) {}

		final int scriptIndex = rawLocale.indexOf("_#");
		if (scriptIndex > 0) {
			try {
				return LocaleUtils.toLocale(rawLocale.substring(0, scriptIndex));
			}
			catch (IllegalArgumentException e) {}
		}

		final Locale languageTagLocale = Locale.forLanguageTag(rawLocale.replace('_', '-'));
		if (StringUtils.isNotBlank(languageTagLocale.getLanguage())) return languageTagLocale;

		return defaultLocale;
	}
}
