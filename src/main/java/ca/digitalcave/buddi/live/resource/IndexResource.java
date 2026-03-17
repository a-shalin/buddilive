package ca.digitalcave.buddi.live.resource;

import java.io.StringWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.apache.ibatis.session.SqlSession;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import ca.digitalcave.buddi.live.BuddiApplication;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Users;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.Account;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import ca.digitalcave.moss.restlet.model.AuthUser;
import ca.digitalcave.moss.restlet.plugin.AuthenticationConfiguration;
import ca.digitalcave.moss.restlet.plugin.AuthenticationHelper;
import ca.digitalcave.moss.restlet.plugin.ExtraFieldsDirective;
import ca.digitalcave.moss.restlet.CookieAuthenticator;
import ca.digitalcave.moss.restlet.resource.LoginResource;
import ca.digitalcave.moss.restlet.util.LocalizationUtil;
import ca.digitalcave.moss.restlet.util.OverridableResourceBundle;

public class IndexResource extends ServerResource {

	@Override
	protected void doInit() throws ResourceException {
		getVariants().add(new Variant(MediaType.TEXT_HTML));
	}

	@Override
	protected Representation get(Variant variant) throws ResourceException {
		final HashMap<String, Object> dataModel = new HashMap<String, Object>();
		final BuddiApplication application = (BuddiApplication) getApplication();
		final SqlSession sqlSession = application.getSqlSessionFactory().openSession();
		try {
			final User user = (User) getRequest().getClientInfo().getUser();
			if (user != null){
				final int encryptionVersion = sqlSession.getMapper(Users.class).selectEncryptionVersion(user);

				if (encryptionVersion == 1){
					DataUpdater.upgradeEncryptionFrom1(user, sqlSession);
				}

				final List<Account> accounts = sqlSession.getMapper(Sources.class).selectAccounts(user);
				if (accounts.size() == 0) dataModel.put("newUser", "true");

				sqlSession.getMapper(Users.class).updateUserLoginTime(user);

				sqlSession.commit();
			}
		} catch (CryptoException e){
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		} catch (DatabaseException e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		} finally {
			sqlSession.close();
		}

		final User user = (User) getClientInfo().getUser();
		if (getClientInfo().getUser() != null && (!user.isTwoFactorRequired() || user.getTwoFactorBackupCodes().size() > 0)) {
			final ChallengeResponse challengeResponse = getChallengeResponse();
			dataModel.put("user", getClientInfo().getUser());
			dataModel.put("translationsJson", serializeTranslationsJson(LocaleUtil.getTranslation(getRequest())));
			dataModel.put("userConfigJson", serializeUserConfigJson(user, challengeResponse));
		}
		else {
			dataModel.put("authConfigJson", serializeAuthConfigJson());
			dataModel.put("authI18nJson", serializeAuthI18nJson());
		}

		dataModel.put("requestAttributes", getRequestAttributes());
		dataModel.put("systemProperties", ((BuddiApplication) getApplication()).getSystemProperties());
		dataModel.put("translation", LocaleUtil.getTranslation(getRequest()));

		return new TemplateRepresentation("/index.html", ((BuddiApplication) getApplication()).getFreemarkerConfiguration(), dataModel, variant.getMediaType());
	}

	private String serializeTranslationsJson(ResourceBundle bundle) {
		try {
			final StringWriter sw = new StringWriter();
			final JsonGenerator g = new JsonFactory().createGenerator(sw);
			g.writeStartObject();
			final Enumeration<String> keys = bundle.getKeys();
			while (keys.hasMoreElements()) {
				final String key = keys.nextElement();
				g.writeStringField(key, bundle.getString(key));
			}
			g.writeEndObject();
			g.close();
			return sw.toString();
		} catch (Exception e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		}
	}

	private String serializeUserConfigJson(final User user, final ChallengeResponse challengeResponse) {
		try {
			final StringWriter sw = new StringWriter();
			final JsonGenerator g = new JsonFactory().createGenerator(sw);
			final long sessionTimeoutMillis = CookieAuthenticator.getCookieTimeoutMillis(challengeResponse);
			g.writeStartObject();
			g.writeStringField("extDateFormat", user.getExtDateFormat());
			g.writeBooleanField("premium", true);
			g.writeBooleanField("encrypted", user.isEncrypted());
			g.writeStringField("decimalSeparator", user.getDecimalSeparator());
			g.writeStringField("thousandSeparator", user.getThousandSeparator());
			g.writeStringField("currencySymbol", user.getCurrencySymbol());
			g.writeStringField("plaintextIdentifier", user.getPlaintextIdentifier());
			g.writeNumberField("sessionTimeoutMillis", sessionTimeoutMillis);
			g.writeNumberField("sessionRefreshWindowMillis", CookieAuthenticator.COOKIE_REFRESH_WINDOW_MILLIS);
			g.writeEndObject();
			g.close();
			return sw.toString();
		} catch (Exception e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		}
	}

	private String serializeAuthConfigJson() {
		try {
			final BuddiApplication application = (BuddiApplication) getApplication();
			final AuthenticationHelper helper = application.getAuthenticationHelper();
			final AuthenticationConfiguration config = helper.getConfig();
			final AuthUser authUser = (AuthUser) getRequest().getClientInfo().getUser();
			final ChallengeResponse cr = getChallengeResponse();

			Locale locale;
			try {
				locale = LocalizationUtil.getLocale(getPreferredVariant(getVariants()));
			} catch (Exception e2) {
				locale = Locale.getDefault();
			}
			final ResourceBundle i18n = new OverridableResourceBundle(
				(config.i18nBaseCustom == null ? null : ResourceBundle.getBundle(config.i18nBaseCustom, locale)),
				ResourceBundle.getBundle("ca.digitalcave.moss.restlet.i18n", locale)
			);

			final StringWriter sw = new StringWriter();
			final JsonGenerator g = new JsonFactory().createGenerator(sw);
			g.writeStartObject();

			g.writeBooleanField("showLogin", config.showLogin);
			g.writeBooleanField("showRegister", config.showRegister);
			g.writeBooleanField("showForgotPassword", config.showForgotPassword);
			g.writeBooleanField("showForgotUsername", config.showForgotUsername);
			g.writeBooleanField("showCookieWarning", config.showCookieWarning);
			g.writeBooleanField("showRemember", config.showRemember);
			g.writeBooleanField("showDisableIpLock", config.showDisableIpLock);

			final String routerAttachPoint = getReference().toString().replace(getRootRef().toString(), "").replaceFirst("/[^/]*$", "").replaceFirst("^/", "");
			g.writeStringField("routerAttachPoint", "authentication");

			final String nextStep = LoginResource.getNextStep(cr, authUser);
			g.writeStringField("activeItem", nextStep != null ? nextStep : "authenticate");

			if (config.applicationLoaderPaths != null) {
				g.writeObjectFieldStart("applicationLoaderPaths");
				for (Map.Entry<String, String> entry : config.applicationLoaderPaths.entrySet()) {
					g.writeStringField(entry.getKey(), entry.getValue());
				}
				g.writeEndObject();
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

			writeExtraFields(g, "extraRegisterStep1Fields", config.extraRegisterStep1Fields, i18n);
			writeExtraFields(g, "extraRegisterStep2Fields", config.extraRegisterStep2Fields, i18n);
			writeExtraFields(g, "extraforgotPasswordStep1PanelFields", config.extraforgotPasswordStep1PanelFields, i18n);
			writeExtraFields(g, "extraforgotPasswordStep2PanelFields", config.extraforgotPasswordStep2PanelFields, i18n);
			writeExtraFields(g, "extraForgotUsernameStep1PanelFields", config.extraForgotUsernameStep1PanelFields, i18n);

			g.writeEndObject();
			g.close();
			return sw.toString();
		} catch (Exception e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		}
	}

	private void writeExtraFields(JsonGenerator g, String fieldName, ExtraFieldsDirective directive, ResourceBundle i18n) throws Exception {
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

	private String serializeAuthI18nJson() {
		try {
			final BuddiApplication application = (BuddiApplication) getApplication();
			final AuthenticationHelper helper = application.getAuthenticationHelper();
			final AuthenticationConfiguration config = helper.getConfig();
			Locale locale;
			try {
				locale = LocalizationUtil.getLocale(getPreferredVariant(getVariants()));
			} catch (Exception e2) {
				locale = Locale.getDefault();
			}

			final ResourceBundle i18n = new OverridableResourceBundle(
				(config.i18nBaseCustom == null ? null : ResourceBundle.getBundle(config.i18nBaseCustom, locale)),
				ResourceBundle.getBundle("ca.digitalcave.moss.restlet.i18n", locale)
			);

			return serializeTranslationsJson(i18n);
		} catch (Exception e) {
			throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
		}
	}
}
