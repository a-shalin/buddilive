package ca.digitalcave.buddi.live.api.converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import ca.digitalcave.buddi.live.api.dto.StoreResponseDto;
import ca.digitalcave.buddi.live.api.dto.StoreResponseDto.StoreItemDto;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.FormatUtil;

@Component
public class StoreResponseConverter {

	private static final String[] COMMON_CURRENCIES = new String[]{
			"CAD",
			"USD",
			"EUR",
			"GBP",
			"AUD",
	};
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

	public StoreResponseDto convertCurrencies() {
		final Set<String> allCurrencies = new TreeSet<>();
		for (final Locale locale : Locale.getAvailableLocales()) {
			try {
				allCurrencies.add(Currency.getInstance(locale).getCurrencyCode());
			}
			catch (final Exception e) {
			}
		}
		allCurrencies.removeAll(Arrays.asList(COMMON_CURRENCIES));

		final List<StoreItemDto> data = new ArrayList<>();
		for (final String currency : COMMON_CURRENCIES) {
			data.add(new StoreItemDto(currency, currency, null));
		}
		data.add(new StoreItemDto("---", "", "color: " + FormatUtil.HTML_GRAY + ";"));

		for (final String currency : allCurrencies) {
			data.add(new StoreItemDto(currency, currency, null));
		}

		return new StoreResponseDto(true, data);
	}

	public StoreResponseDto convertLocales(final User user) {
		final Locale displayLocale = (user != null && user.getLocale() != null) ? user.getLocale() : Locale.ENGLISH;
		final Set<Locale> allLocales = new TreeSet<>((o1, o2) -> {
			if (o1 == null || o2 == null) {
				return 0;
			}
			return o1.getDisplayName(displayLocale).compareTo(o2.getDisplayName(displayLocale));
		});
		allLocales.addAll(Arrays.asList(SUPPORTED_LOCALES));
		allLocales.removeAll(Arrays.asList(COMMON_LOCALES));

		final List<StoreItemDto> data = new ArrayList<>();
		for (final Locale locale : COMMON_LOCALES) {
			data.add(new StoreItemDto(locale.getDisplayName(displayLocale), toLegacyLocaleString(locale), null));
		}

		if (!allLocales.isEmpty()) {
			data.add(new StoreItemDto("---", "", "color: " + FormatUtil.HTML_GRAY + ";"));
		}

		for (final Locale locale : allLocales) {
			data.add(new StoreItemDto(locale.getDisplayName(displayLocale), toLegacyLocaleString(locale), null));
		}

		return new StoreResponseDto(true, data);
	}

	private String toLegacyLocaleString(final Locale locale) {
		if (locale == null) {
			return "";
		}
		if (StringUtils.isBlank(locale.getLanguage())) {
			return locale.toString();
		}
		if (StringUtils.isBlank(locale.getCountry())) {
			return locale.getLanguage();
		}
		if (StringUtils.isBlank(locale.getVariant())) {
			return locale.getLanguage() + "_" + locale.getCountry();
		}
		return locale.getLanguage() + "_" + locale.getCountry() + "_" + locale.getVariant();
	}
}
