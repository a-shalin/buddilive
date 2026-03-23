package ca.digitalcave.buddi.live.controller;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.FormatUtil;

@RestController
@RequestMapping("/stores")
public class StoreController {

	private static final Locale[] SUPPORTED_LOCALES = new Locale[]{
			new Locale("de"),
			new Locale("el"),
			Locale.US,
			new Locale("es"),
			new Locale("es", "MX"),
			new Locale("fr"),
			new Locale("he"),
			new Locale("it"),
			new Locale("nl"),
			new Locale("no"),
			new Locale("pt"),
			new Locale("pt", "BR"),
			new Locale("ru"),
			new Locale("sr"),
			new Locale("sv"),
	};
	private static final Locale[] COMMON_LOCALES = new Locale[]{
			Locale.US,
			new Locale("es"),
			new Locale("de"),
			new Locale("it"),
	};

	@GetMapping("/currencies")
	public String currencies() {
		final String[] commonCurrencies = new String[]{
				Currency.getInstance("CAD").getCurrencyCode(),
				Currency.getInstance("USD").getCurrencyCode(),
				Currency.getInstance("EUR").getCurrencyCode(),
				Currency.getInstance("GBP").getCurrencyCode(),
				Currency.getInstance("AUD").getCurrencyCode(),
		};
		final Set<String> allCurrencies = new TreeSet<>();
		for (Locale locale : Locale.getAvailableLocales()) {
			try {
				allCurrencies.add(Currency.getInstance(locale).getCurrencyCode());
			}
			catch (Exception e) {}
		}
		allCurrencies.removeAll(Arrays.asList(commonCurrencies));

		final JSONObject result = new JSONObject();
		result.put("success", true);

		for (String currency : commonCurrencies) {
			final JSONObject entry = new JSONObject();
			entry.put("text", currency);
			entry.put("value", currency);
			result.append("data", entry);
		}

		final JSONObject separator = new JSONObject();
		separator.put("text", "---");
		separator.put("value", "");
		separator.put("style", "color: " + FormatUtil.HTML_GRAY + ";");
		result.append("data", separator);

		for (String currency : allCurrencies) {
			final JSONObject entry = new JSONObject();
			entry.put("text", currency);
			entry.put("value", currency);
			result.append("data", entry);
		}
		return result.toString();
	}

	@GetMapping("/locales")
	public String locales(@AuthenticationPrincipal User user) {
		final Locale displayLocale = (user != null && user.getLocale() != null) ? user.getLocale() : Locale.ENGLISH;
		final Set<Locale> allLocales = new TreeSet<>(new Comparator<Locale>() {
			@Override
			public int compare(Locale o1, Locale o2) {
				if (o1 == null || o2 == null) return 0;
				return o1.getDisplayName(displayLocale).compareTo(o2.getDisplayName(displayLocale));
			}
		});
		allLocales.addAll(Arrays.asList(SUPPORTED_LOCALES));
		allLocales.removeAll(Arrays.asList(COMMON_LOCALES));

		final JSONObject result = new JSONObject();
		result.put("success", true);

		for (Locale locale : COMMON_LOCALES) {
			final JSONObject entry = new JSONObject();
			entry.put("text", locale.getDisplayName(displayLocale));
			entry.put("value", toLegacyLocaleString(locale));
			result.append("data", entry);
		}

		if (!allLocales.isEmpty()) {
			final JSONObject separator = new JSONObject();
			separator.put("text", "---");
			separator.put("value", "");
			separator.put("style", "color: " + FormatUtil.HTML_GRAY + ";");
			result.append("data", separator);
		}

		for (Locale locale : allLocales) {
			final JSONObject entry = new JSONObject();
			entry.put("text", locale.getDisplayName(displayLocale));
			entry.put("value", toLegacyLocaleString(locale));
			result.append("data", entry);
		}
		return result.toString();
	}

	private String toLegacyLocaleString(Locale locale) {
		if (locale == null) return "";
		if (StringUtils.isBlank(locale.getLanguage())) return locale.toString();
		if (StringUtils.isBlank(locale.getCountry())) return locale.getLanguage();
		if (StringUtils.isBlank(locale.getVariant())) return locale.getLanguage() + "_" + locale.getCountry();
		return locale.getLanguage() + "_" + locale.getCountry() + "_" + locale.getVariant();
	}
}