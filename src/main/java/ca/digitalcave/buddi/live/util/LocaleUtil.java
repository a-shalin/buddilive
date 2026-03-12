package ca.digitalcave.buddi.live.util;

import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.restlet.Request;

import ca.digitalcave.buddi.live.model.User;

public class LocaleUtil {
	private static final String key = "buddilive-translations";
	
	public static ResourceBundle getTranslation(){
		return getTranslation(null);
	}
	
	public static ResourceBundle getTranslation(Request r){
		if (r == null){
			return ResourceBundle.getBundle("i18n");
		}
		if (r.getAttributes().get(key) == null){
			final User user = (User) r.getClientInfo().getUser();
			ResourceBundle translations;
			if (user != null && user.getLocale() != null){
				translations = ResourceBundle.getBundle("i18n", user.getLocale());
			}
			else {
				translations = ResourceBundle.getBundle("i18n");
			}
			r.getAttributes().put(key, translations);
		}
		return ((ResourceBundle) r.getAttributes().get(key));
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
