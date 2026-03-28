package ca.digitalcave.buddi.live.controller;

import ca.digitalcave.buddi.live.config.AssetVersionTokenProvider;
import ca.digitalcave.buddi.live.db.*;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.Account;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.security.CookieAuthenticationToken;
import ca.digitalcave.buddi.live.security.CookieUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.auth.config.AuthenticationConfiguration;
import ca.digitalcave.moss.auth.i18n.OverridableResourceBundle;
import ca.digitalcave.moss.auth.service.AuthenticationHelper;
import ca.digitalcave.moss.auth.template.ExtraFieldsDirective;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import java.io.StringWriter;
import java.util.*;

@Controller
public class IndexController {

	private static final String CACHE_CONTROL_HEADER = "Cache-Control";
	private static final String CACHE_CONTROL_NO_STORE_VALUE = "no-cache, no-store, max-age=0, must-revalidate";
	private static final String PRAGMA_HEADER = "Pragma";
	private static final String PRAGMA_NO_CACHE_VALUE = "no-cache";
	private static final String EXPIRES_HEADER = "Expires";

	@Autowired
	private Users users;

	@Autowired
	private Sources sources;

	@Autowired
	private Transactions transactions;

	@Autowired
	private Entries entries;

	@Autowired
	private ScheduledTransactions scheduledTransactions;

	@Autowired
	private Crypto crypto;

	@Autowired
	private AuthenticationHelper authenticationHelper;

	@Autowired
	@Qualifier("mailProperties")
	private Properties mailProperties;

	@Autowired
	private AssetVersionTokenProvider assetVersionTokenProvider;

	@GetMapping("/index")
	@Transactional
	public String index(@AuthenticationPrincipal final User user, final Model model, final HttpServletResponse response) {
		response.setHeader(CACHE_CONTROL_HEADER, CACHE_CONTROL_NO_STORE_VALUE);
		response.setHeader(PRAGMA_HEADER, PRAGMA_NO_CACHE_VALUE);
		response.setDateHeader(EXPIRES_HEADER, 0L);

		try {
			if (user != null) {
				final int encryptionVersion = users.selectEncryptionVersion(user);
				if (encryptionVersion == 1) {
					DataUpdater.upgradeEncryptionFrom1(user, sources, entries, transactions, scheduledTransactions, users, crypto);
				}

				final List<Account> accounts = sources.selectAccounts(user);
				if (accounts.size() == 0) model.addAttribute("newUser", "true");

				users.updateUserLoginTime(user);
			}
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
		catch (DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}

		if (user != null && (!user.isTwoFactorRequired() || user.getTwoFactorBackupCodes().size() > 0)) {
			final Map<String, String> cookieParams = getCookieParams();
			model.addAttribute("user", user);
			model.addAttribute("translationsJson", serializeTranslationsJson(LocaleUtil.getTranslation(user)));
			model.addAttribute("userConfigJson", serializeUserConfigJson(user, cookieParams));
		}
		else {
			model.addAttribute("authConfigJson", serializeAuthConfigJson());
			model.addAttribute("authI18nJson", serializeAuthI18nJson());
		}

		model.addAttribute("translation", LocaleUtil.getTranslation(user != null ? user : new User()));
		model.addAttribute("buildDate", System.getProperty("BUILD_DATE"));
		model.addAttribute("version", System.getProperty("VERSION"));
		model.addAttribute("assetVersionToken", assetVersionTokenProvider.getToken());

		return "index";
	}

	private Map<String, String> getCookieParams() {
		final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof CookieAuthenticationToken) {
			return ((CookieAuthenticationToken) auth).getCookieParams();
		}
		return new HashMap<>();
	}

	private String serializeTranslationsJson(final ResourceBundle bundle) {
		try {
			final StringWriter sw = new StringWriter();
			try (JsonGenerator g = new JsonFactory().createGenerator(sw)) {
				g.writeStartObject();
				final Enumeration<String> keys = bundle.getKeys();
				while (keys.hasMoreElements()) {
					final String key = keys.nextElement();
					g.writeStringField(key, bundle.getString(key));
				}
				g.writeEndObject();
			}
			return sw.toString();
		}
		catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	private String serializeUserConfigJson(final User user, final Map<String, String> cookieParams) {
		try {
			final StringWriter sw = new StringWriter();
			try (JsonGenerator g = new JsonFactory().createGenerator(sw)) {
				final boolean remember = Boolean.parseBoolean(cookieParams.getOrDefault(CookieUtil.FIELD_REMEMBER, "false"));
				final long sessionTimeoutMillis = CookieUtil.getCookieTimeoutMillis(remember);
				g.writeStartObject();
				g.writeStringField("extDateFormat", user.getExtDateFormat());
				g.writeBooleanField("premium", true);
				g.writeBooleanField("encrypted", user.isEncrypted());
				g.writeStringField("decimalSeparator", user.getDecimalSeparator());
				g.writeStringField("thousandSeparator", user.getThousandSeparator());
				g.writeStringField("currencySymbol", user.getCurrencySymbol());
				g.writeStringField("plaintextIdentifier", user.getPlaintextIdentifier());
				g.writeNumberField("sessionTimeoutMillis", sessionTimeoutMillis);
				g.writeNumberField("sessionRefreshWindowMillis", CookieUtil.COOKIE_REFRESH_WINDOW_MILLIS);
				g.writeEndObject();
			}
			return sw.toString();
		}
		catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	private String serializeAuthConfigJson() {
		try {
			final AuthenticationConfiguration config = authenticationHelper.getConfig();
			final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			final Map<String, String> cookieParams = getCookieParams();

			final StringWriter sw = new StringWriter();
			try (JsonGenerator g = new JsonFactory().createGenerator(sw)) {
				g.writeStartObject();

				g.writeBooleanField("showLogin", config.showLogin);
				g.writeBooleanField("showRegister", config.showRegister);
				g.writeBooleanField("showForgotPassword", config.showForgotPassword);
				g.writeBooleanField("showForgotUsername", config.showForgotUsername);
				g.writeBooleanField("showCookieWarning", config.showCookieWarning);
				g.writeBooleanField("showRemember", config.showRemember);
				g.writeBooleanField("showDisableIpLock", config.showDisableIpLock);
				g.writeBooleanField("directRegistration", config.directRegistration);

				g.writeStringField("routerAttachPoint", "authentication");

				final String nextStep = getNextStep(cookieParams, auth);
				g.writeStringField("activeItem", nextStep != null ? nextStep : "authenticate");

				if (config.applicationLoaderPaths != null) {
					g.writeObjectFieldStart("applicationLoaderPaths");
					for (Map.Entry<String, String> entry : config.applicationLoaderPaths.entrySet()) {
						g.writeStringField(entry.getKey(), entry.getValue());
					}
					g.writeEndObject();
				}

				if (config.applicationRequires != null) {
					g.writeArrayFieldStart("applicationRequires");
					for (String r : config.applicationRequires) {
						g.writeString(r);
					}
					g.writeEndArray();
				}

				if (config.applicationViews != null) {
					g.writeArrayFieldStart("applicationViews");
					for (String v : config.applicationViews) {
						g.writeString(v);
					}
					g.writeEndArray();
				}

				if (config.applicationControllers != null) {
					g.writeArrayFieldStart("applicationControllers");
					for (String c : config.applicationControllers) {
						g.writeString(c);
					}
					g.writeEndArray();
				}

				if (config.applicationModels != null) {
					g.writeArrayFieldStart("applicationModels");
					for (String m : config.applicationModels) {
						g.writeString(m);
					}
					g.writeEndArray();
				}

				final Locale locale = Locale.getDefault();
				final ResourceBundle i18n = new OverridableResourceBundle(
					(config.i18nBaseCustom == null ? null : ResourceBundle.getBundle(config.i18nBaseCustom, locale)),
					ResourceBundle.getBundle("ca.digitalcave.moss.auth.i18n", locale)
				);

				writeExtraFields(g, "extraRegisterStep1Fields", config.extraRegisterStep1Fields, i18n);
				writeExtraFields(g, "extraRegisterStep2Fields", config.extraRegisterStep2Fields, i18n);
				writeExtraFields(g, "extraforgotPasswordStep1PanelFields", config.extraforgotPasswordStep1PanelFields, i18n);
				writeExtraFields(g, "extraforgotPasswordStep2PanelFields", config.extraforgotPasswordStep2PanelFields, i18n);
				writeExtraFields(g, "extraForgotUsernameStep1PanelFields", config.extraForgotUsernameStep1PanelFields, i18n);

				g.writeEndObject();
			}
			return sw.toString();
		}
		catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	private void writeExtraFields(final JsonGenerator g, final String fieldName, final ExtraFieldsDirective directive, final ResourceBundle i18n) throws Exception {
		if (directive == null) return;
		final StringWriter fieldWriter = new StringWriter();
		directive.writeFields(fieldWriter, i18n);
		String fieldsJson = fieldWriter.toString().trim();
		if (fieldsJson.endsWith(",")) {
			fieldsJson = fieldsJson.substring(0, fieldsJson.length() - 1);
		}
		g.writeFieldName(fieldName);
		g.writeRawValue("[" + fieldsJson + "]");
	}

	private String getNextStep(final Map<String, String> params, final Authentication auth) {
		final User user = (auth != null && auth.getPrincipal() instanceof User) ? (User) auth.getPrincipal() : null;

		if (params != null
				&& CookieUtil.isPrimaryAuthenticationValid(params)
				&& CookieUtil.isSecondaryAuthenticationValid(params)
				&& CookieUtil.isPasswordExpired(params)) {
			return "passwordExpired";
		}
		else if (CookieUtil.isAuthenticationValid(params)
				&& CookieUtil.isTwoFactorSetup(params)
				&& user != null
				&& user.getTwoFactorBackupCodes().size() == 0) {
			return "totpBackupCodes";
		}
		else if (CookieUtil.isPrimaryAuthenticationValid(params)
				&& !CookieUtil.isTwoFactorSetup(params)
				&& !CookieUtil.isImpersonating(params)
				&& CookieUtil.isTwoFactorRequired(params)) {
			return "totpSetup";
		}
		else if (CookieUtil.isPrimaryAuthenticationValid(params)
				&& CookieUtil.isTwoFactorSetup(params)
				&& !CookieUtil.isSecondaryAuthenticationValid(params)) {
			return "totpToken";
		}

		return null;
	}

	private String serializeAuthI18nJson() {
		try {
			final AuthenticationConfiguration config = authenticationHelper.getConfig();
			final Locale locale = Locale.getDefault();
			final ResourceBundle i18n = new OverridableResourceBundle(
				(config.i18nBaseCustom == null ? null : ResourceBundle.getBundle(config.i18nBaseCustom, locale)),
				ResourceBundle.getBundle("ca.digitalcave.moss.auth.i18n", locale)
			);
			return serializeTranslationsJson(i18n);
		}
		catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}
