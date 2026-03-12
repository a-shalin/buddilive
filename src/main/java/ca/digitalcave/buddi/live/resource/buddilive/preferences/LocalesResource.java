package ca.digitalcave.buddi.live.resource.buddilive.preferences;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.FormatUtil;

public class LocalesResource extends ServerResource {
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

	@Override
	protected void doInit() throws ResourceException {
		getVariants().add(new Variant(MediaType.APPLICATION_JSON));
	}

	@Override
	protected Representation get(Variant variant) throws ResourceException {
		final User user = (User) getRequest().getClientInfo().getUser();
		final Locale displayLocale = (user != null && user.getLocale() != null) ? user.getLocale() : Locale.ENGLISH;
		final Set<Locale> allLocales = new TreeSet<Locale>(new Comparator<Locale>() {
			@Override
			public int compare(Locale o1, Locale o2) {
				if (o1 == null || o2 == null) return 0;
				return o1.getDisplayName(displayLocale).compareTo(o2.getDisplayName(displayLocale));
			}
		});
		allLocales.addAll(Arrays.asList(SUPPORTED_LOCALES));
		allLocales.removeAll(Arrays.asList(COMMON_LOCALES));
		
		try {
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
			return new JsonRepresentation(result);
		}
		catch (JSONException e){
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		}
	}

	private String toLegacyLocaleString(Locale locale) {
		if (locale == null) return "";
		if (StringUtils.isBlank(locale.getLanguage())) return locale.toString();
		if (StringUtils.isBlank(locale.getCountry())) return locale.getLanguage();
		if (StringUtils.isBlank(locale.getVariant())) return locale.getLanguage() + "_" + locale.getCountry();
		return locale.getLanguage() + "_" + locale.getCountry() + "_" + locale.getVariant();
	}
}
