package ca.digitalcave.moss.restlet.util;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.commons.lang3.time.DateUtils;
import org.restlet.data.CharacterSet;
import org.restlet.data.Language;
import org.restlet.data.MediaType;
import org.restlet.representation.Variant;

public class LocalizationUtil {

	public static final Language[] AVAILABLE = new Language[] {
		new Language("en"),			//This is the default locale, to be used when the client does not send a language request header.
		new Language("en-AU"),
		new Language("en-BZ"),
		new Language("en-CA"),
		new Language("en-CB"),
		new Language("en-GB"),
		new Language("en-IE"),
		new Language("en-JM"),
		new Language("en-NZ"),
		new Language("en-PH"),
		new Language("en-TT"),
		new Language("en-US"),
		new Language("en-ZA"),
		new Language("en-ZW"),
		new Language("af"),
		new Language("af-ZA"),
		new Language("ar"),
		new Language("ar-AE"),
		new Language("ar-BH"),
		new Language("ar-DZ"),
		new Language("ar-EG"),
		new Language("ar-IQ"),
		new Language("ar-JO"),
		new Language("ar-KW"),
		new Language("ar-LB"),
		new Language("ar-LY"),
		new Language("ar-MA"),
		new Language("ar-OM"),
		new Language("ar-QA"),
		new Language("ar-SA"),
		new Language("ar-SY"),
		new Language("ar-TN"),
		new Language("ar-YE"),
		new Language("az"),
		new Language("az-AZ"),
		new Language("az-AZ"),
		new Language("be"),
		new Language("be-BY"),
		new Language("bg"),
		new Language("bg-BG"),
		new Language("bs-BA"),
		new Language("ca"),
		new Language("ca-ES"),
		new Language("cs"),
		new Language("cs-CZ"),
		new Language("cy"),
		new Language("cy-GB"),
		new Language("da"),
		new Language("da-DK"),
		new Language("de"),
		new Language("de-AT"),
		new Language("de-CH"),
		new Language("de-DE"),
		new Language("de-LI"),
		new Language("de-LU"),
		new Language("dv"),
		new Language("dv-MV"),
		new Language("el"),
		new Language("el-GR"),
		new Language("eo"),
		new Language("es"),
		new Language("es-AR"),
		new Language("es-BO"),
		new Language("es-CL"),
		new Language("es-CO"),
		new Language("es-CR"),
		new Language("es-DO"),
		new Language("es-EC"),
		new Language("es-ES"),
		new Language("es-ES"),
		new Language("es-GT"),
		new Language("es-HN"),
		new Language("es-MX"),
		new Language("es-NI"),
		new Language("es-PA"),
		new Language("es-PE"),
		new Language("es-PR"),
		new Language("es-PY"),
		new Language("es-SV"),
		new Language("es-UY"),
		new Language("es-VE"),
		new Language("et"),
		new Language("et-EE"),
		new Language("eu"),
		new Language("eu-ES"),
		new Language("fa"),
		new Language("fa-IR"),
		new Language("fi"),
		new Language("fi-FI"),
		new Language("fo"),
		new Language("fo-FO"),
		new Language("fr"),
		new Language("fr-BE"),
		new Language("fr-CA"),
		new Language("fr-CH"),
		new Language("fr-FR"),
		new Language("fr-LU"),
		new Language("fr-MC"),
		new Language("gl"),
		new Language("gl-ES"),
		new Language("gu"),
		new Language("gu-IN"),
		new Language("he"),
		new Language("he-IL"),
		new Language("hi"),
		new Language("hi-IN"),
		new Language("hr"),
		new Language("hr-BA"),
		new Language("hr-HR"),
		new Language("hu"),
		new Language("hu-HU"),
		new Language("hy"),
		new Language("hy-AM"),
		new Language("id"),
		new Language("id-ID"),
		new Language("is"),
		new Language("is-IS"),
		new Language("it"),
		new Language("it-CH"),
		new Language("it-IT"),
		new Language("ja"),
		new Language("ja-JP"),
		new Language("ka"),
		new Language("ka-GE"),
		new Language("kk"),
		new Language("kk-KZ"),
		new Language("kn"),
		new Language("kn-IN"),
		new Language("ko"),
		new Language("ko-KR"),
		new Language("kok"),
		new Language("kok-IN"),
		new Language("ky"),
		new Language("ky-KG"),
		new Language("lt"),
		new Language("lt-LT"),
		new Language("lv"),
		new Language("lv-LV"),
		new Language("mi"),
		new Language("mi-NZ"),
		new Language("mk"),
		new Language("mk-MK"),
		new Language("mn"),
		new Language("mn-MN"),
		new Language("mr"),
		new Language("mr-IN"),
		new Language("ms"),
		new Language("ms-BN"),
		new Language("ms-MY"),
		new Language("mt"),
		new Language("mt-MT"),
		new Language("nb"),
		new Language("nb-NO"),
		new Language("nl"),
		new Language("nl-BE"),
		new Language("nl-NL"),
		new Language("nn-NO"),
		new Language("ns"),
		new Language("ns-ZA"),
		new Language("pa"),
		new Language("pa-IN"),
		new Language("pl"),
		new Language("pl-PL"),
		new Language("ps"),
		new Language("ps-AR"),
		new Language("pt"),
		new Language("pt-BR"),
		new Language("pt-PT"),
		new Language("qu"),
		new Language("qu-BO"),
		new Language("qu-EC"),
		new Language("qu-PE"),
		new Language("ro"),
		new Language("ro-RO"),
		new Language("ru"),
		new Language("ru-RU"),
		new Language("sa"),
		new Language("sa-IN"),
		new Language("se"),
		new Language("se-FI"),
		new Language("se-FI"),
		new Language("se-FI"),
		new Language("se-NO"),
		new Language("se-NO"),
		new Language("se-NO"),
		new Language("se-SE"),
		new Language("se-SE"),
		new Language("se-SE"),
		new Language("sk"),
		new Language("sk-SK"),
		new Language("sl"),
		new Language("sl-SI"),
		new Language("sq"),
		new Language("sq-AL"),
		new Language("sr-BA"),
		new Language("sr-BA"),
		new Language("sr-SP"),
		new Language("sr-SP"),
		new Language("sv"),
		new Language("sv-FI"),
		new Language("sv-SE"),
		new Language("sw"),
		new Language("sw-KE"),
		new Language("syr"),
		new Language("syr-SY"),
		new Language("ta"),
		new Language("ta-IN"),
		new Language("te"),
		new Language("te-IN"),
		new Language("th"),
		new Language("th-TH"),
		new Language("tl"),
		new Language("tl-PH"),
		new Language("tn"),
		new Language("tn-ZA"),
		new Language("tr"),
		new Language("tr-TR"),
		new Language("tt"),
		new Language("tt-RU"),
		new Language("ts"),
		new Language("uk"),
		new Language("uk-UA"),
		new Language("ur"),
		new Language("ur-PK"),
		new Language("uz"),
		new Language("uz-UZ"),
		new Language("uz-UZ"),
		new Language("vi"),
		new Language("vi-VN"),
		new Language("xh"),
		new Language("xh-ZA"),
		new Language("zh"),
		new Language("zh-CN"),
		new Language("zh-HK"),
		new Language("zh-MO"),
		new Language("zh-SG"),
		new Language("zh-TW"),
		new Language("zu"),
		new Language("zu-ZA")
	};
	
	public static Locale toLocale(Language language) {
		if (language.getSubTags().size() == 0) {
			return new Locale(language.getPrimaryTag());
		} else {
			return new Locale(language.getPrimaryTag(), language.getSubTags().get(0));
		}
	}
	
	public static Locale getLocale(Variant variant) {
		return toLocale(variant.getLanguages().get(0));
	}
	
	public static ResourceBundle getBundle(Variant variant) {
		return getBundle(getLocale(variant));
	}
	
	public static ResourceBundle getBundle(Locale locale) {
		return ResourceBundle.getBundle("i18n", locale);
	}

	public static void addVariants(List<Variant> variants, MediaType mediaType) {
		for (Language lang : LocalizationUtil.AVAILABLE) {
			final Variant variant = new Variant(mediaType);
			variant.setCharacterSet(CharacterSet.UTF_8);
			variant.setLanguages(Collections.singletonList(lang));
			variants.add(variant);
		}
	}
	
	public static String toDateTimeString(Date d, int timeOffset, Locale l) {
		if (d == null) return "";

		ResourceBundle i18n = getBundle(l);
		d = DateUtils.addMilliseconds(d, timeOffset);
		return new SimpleDateFormat(i18n.getString("javaDateTimeFormat")).format(d);
	}
	
	public static String toDateString(Date d, int timeOffset, Locale l) {
		if (d == null) return "";
		ResourceBundle i18n = getBundle(l);
		d = DateUtils.addMilliseconds(d, timeOffset);
		return new SimpleDateFormat(i18n.getString("javaDateFormat")).format(d);
	}
}
