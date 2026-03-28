package ca.digitalcave.buddi.live.security;

import ca.digitalcave.buddi.live.api.dto.ExtJsFieldDto;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.moss.auth.config.AuthenticationConfiguration;
import ca.digitalcave.moss.auth.model.AuthUser;
import ca.digitalcave.moss.auth.service.AuthenticationHelper;
import ca.digitalcave.moss.auth.template.ExtraFieldsDirective;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import ca.digitalcave.moss.crypto.DefaultHash;
import ca.digitalcave.moss.crypto.Hash;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail2.core.EmailException;
import org.apache.commons.mail2.jakarta.HtmlEmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Writer;
import java.security.Key;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class BuddiLiveAuthenticationHelper extends AuthenticationHelper {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final BuddiLiveAuthenticationTransactionalService txService;
	private final Properties mailProperties;

	public BuddiLiveAuthenticationHelper(final BuddiLiveAuthenticationTransactionalService txService,
										 final Properties mailProperties,
										 @Value("${buddi.directRegistration:false}") final boolean directRegistration) {
		super(new AuthenticationConfiguration());
		this.txService = txService;
		this.mailProperties = mailProperties;

		getConfig().directRegistration = directRegistration;
		getConfig().showCookieWarning = true;
		getConfig().showForgotUsername = false;
		getConfig().showForgotPassword = !directRegistration;
		getConfig().showRegister = true;
		getConfig().showImpersonate = false;
		getConfig().showDisableIpLock = true;
		getConfig().i18nBaseCustom = "i18n";
		getConfig().totpIssuer = "Buddi Live";
		getConfig().applicationLoaderPaths = new HashMap<>();
		getConfig().applicationLoaderPaths.put("BuddiLive", "buddilive");
		getConfig().applicationRequires = new String[]{
			"BuddiLive.view.component.GenericStoreBackedCombobox",
			"BuddiLive.view.component.LocalesCombobox",
			"BuddiLive.view.component.CurrenciesCombobox",
			"BuddiLive.store.preferences.LocalesComboboxStore",
			"BuddiLive.store.preferences.CurrenciesComboboxStore"
		};

		getConfig().extraRegisterStep1Fields = new ExtraFieldsDirective() {
			@Override
			public void writeFields(final Writer out, final ResourceBundle translation) {
				try {
					final JsonGenerator g = OBJECT_MAPPER.getFactory().createGenerator(out);
					writeExtJsField(g, ExtJsFieldDto.selfDocumentingField(
							translation.getString("HELP_LOCALE"),
							"localescombobox",
							"locale",
							translation.getString("LOCALE"),
							"en_US"));
					writeExtJsField(g, ExtJsFieldDto.selfDocumentingField(
							translation.getString("HELP_CURRENCY"),
							"currenciescombobox",
							"currency",
							translation.getString("CURRENCY"),
							"USD"));
					writeExtJsField(g, ExtJsFieldDto.selfDocumentingCheckbox(
							translation.getString("CREATE_USER_AGREEMENT_REQUIRED"),
							translation.getString("AGREE_TERMS_AND_CONDITIONS"),
							"agree",
							" ",
							""));
					writeExtJsField(g, ExtJsFieldDto.label(
							translation.getString(directRegistration ? "HELP_REGISTER_DIRECT" : "HELP_REGISTER")));
					g.flush();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		};
		getConfig().extraRegisterStep2Fields = new ExtraFieldsDirective() {
			@Override
			public void writeFields(final Writer out, final ResourceBundle translation) {
				try {
					final JsonGenerator g = OBJECT_MAPPER.getFactory().createGenerator(out);
					writeExtJsField(g, ExtJsFieldDto.label(translation.getString("HELP_REGISTER_2")));
					g.flush();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		};
		getConfig().extraforgotPasswordStep1PanelFields = new ExtraFieldsDirective() {
			@Override
			public void writeFields(final Writer out, final ResourceBundle translation) {
				try {
					final JsonGenerator g = OBJECT_MAPPER.getFactory().createGenerator(out);
					writeExtJsField(g, ExtJsFieldDto.label(translation.getString("HELP_RESET_PASSWORD")));
					g.flush();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		};
		getConfig().extraforgotPasswordStep2PanelFields = new ExtraFieldsDirective() {
			@Override
			public void writeFields(final Writer out, final ResourceBundle translation) {
				try {
					final JsonGenerator g = OBJECT_MAPPER.getFactory().createGenerator(out);
					writeExtJsField(g, ExtJsFieldDto.label(translation.getString("HELP_RESET_PASSWORD_2")));
					g.flush();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	//******************* Authentication / User Section *******************//

	@Override
	public AuthUser authenticate(final String applicationName, final String identifier, final String secret) {
		if (identifier == null) {
			return null;
		}

		final User user = txService.selectUserByHashedIdentifier(getHashedUsername(identifier));
		if (user == null) {
			return null;
		}

		boolean authenticated = false;
		boolean legacy = false;

		final String storedSecret = new String(user.getSecret());
		if (storedSecret.startsWith("SHA-512:")) {
			authenticated = DefaultHash.verify(storedSecret, secret);
		}
		else if (storedSecret.startsWith("SHA-256:")) {
			authenticated = DefaultHash.verify(storedSecret, secret);
			legacy = true;
		}

		if (authenticated) {
			if (legacy) {
				try {
					txService.upgradeLegacyUserSecret(user, getHash().generate(secret));
				}
				catch (Exception e) {
					// Keep authentication successful even if legacy hash upgrade fails.
				}
			}

			user.setPlaintextSecret(secret);
			user.setPlaintextIdentifier(identifier);

			return user;
		}

		return null;
	}

	@Override
	public AuthUser selectUser(final String username) {
		return txService.selectUserByHashedIdentifier(getHashedUsername(username));
	}

	@Override
	public List<AuthUser> selectUsers(final String email) {
		throw new RuntimeException("Forgot Username not implemented");
	}

	@Override
	public boolean insertTotpSecret(final String username, final String totpSharedSecret) {
		try {
			txService.insertTotpSecret(getHashedUsername(username), totpSharedSecret);
			return true;
		}
		catch (DatabaseException e) {
			return false;
		}
	}

	@Override
	public void insertTotpBackupCodes(final String username) {
		try {
			txService.insertTotpBackupCodes(getHashedUsername(username));
		}
		catch (DatabaseException e) {
			// Do nothing; this path intentionally does not return detailed error info.
		}
	}

	@Override
	public void updateTotpBackupCodeMarkUsed(final String username, final String backupCode) {
		try {
			txService.updateTotpBackupCodeMarkUsed(getHashedUsername(username), backupCode);
		}
		catch (DatabaseException e) {
			// Do nothing; this path intentionally does not return detailed error info.
		}
	}

	@Override
	public void disableTotp(final String username) {
		try {
			txService.disableTotp(getHashedUsername(username));
		}
		catch (DatabaseException e) {
			// Do nothing; this path intentionally does not return detailed error info.
		}
	}

	@Override
	public String updateActivationKey(final String username, final String activationKey) throws Exception {
		try {
			txService.updateActivationKey(getHashedUsername(username), activationKey);
			return username;
		}
		catch (DatabaseException e) {
			Logger.getLogger(this.getClass().getName()).log(Level.INFO, e.getMessage());
		}
		return null;
	}

	@Override
	public boolean updatePasswordByActivationKey(final String activationKey, final String hashedPassword) {
		try {
			txService.updatePasswordByActivationKey(activationKey, hashedPassword);
			return true;
		}
		catch (DatabaseException e) {
			return false;
		}
	}

	@Override
	public void insertUser(final String email, final String activationKey, final Map<String, String> formParams) throws Exception {
		txService.insertUser(getHashedUsername(email), activationKey, formParams);
	}

	@Override
	public boolean updatePassword(final String username, final String hashedPassword) {
		return false;
	}

	@Override
	public void sendEmail(final String toEmail, final String subject, final String body) {
		final String fromEmail = mailProperties.getProperty("mail.smtp.from");

		try {
			final HtmlEmail email = createEmail(fromEmail, null, toEmail);
			email.setSubject(subject);
			email.setTextMsg(body);
			new Thread(() -> {
				try {
					email.send();
				}
				catch (EmailException e) {
					Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Error sending email", e);
				}
			}).start();
		}
		catch (AddressException e) {
			Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Error parsing email address", e);
		}
		catch (EmailException e) {
			Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Error preparing email", e);
		}
	}

	private HtmlEmail createEmail(final String from, final String replyTo, final String to) throws EmailException, AddressException {
		final HtmlEmail htmlEmail = new HtmlEmail();
		if (StringUtils.isBlank(mailProperties.getProperty("mail.smtp.host"))) {
			throw new EmailException("Parameter mail.smtp.host cannot be blank.");
		}
		htmlEmail.setHostName(mailProperties.getProperty("mail.smtp.host"));
		htmlEmail.setSmtpPort(Integer.parseInt(mailProperties.getProperty("mail.smtp.port", "25")));

		final InternetAddress[] fromAddresses = InternetAddress.parse(from, false);
		if (fromAddresses.length > 0) {
			htmlEmail.setFrom(fromAddresses[0].getAddress(), fromAddresses[0].getPersonal());
		}

		final InternetAddress[] toAddresses = InternetAddress.parse(to, false);
		for (InternetAddress toAddress : toAddresses) {
			htmlEmail.addTo(toAddress.getAddress(), toAddress.getPersonal());
		}

		if (StringUtils.isNotBlank(replyTo)) {
			final InternetAddress[] replyToAddresses = InternetAddress.parse(replyTo);
			for (InternetAddress replyToAddress : replyToAddresses) {
				try {
					htmlEmail.addReplyTo(replyToAddress.getAddress(), replyToAddress.getPersonal());
				}
				catch (Throwable e) {
					Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Invalid replyToAddress " + replyToAddress, e);
				}
			}
		}

		final String emailUser = mailProperties.getProperty("mail.smtp.username");
		final String emailPassword = mailProperties.getProperty("mail.smtp.password", "");
		if (StringUtils.isNotBlank(emailUser)) {
			htmlEmail.setAuthentication(emailUser, emailPassword);
		}
		final boolean startTls = Boolean.parseBoolean(mailProperties.getProperty("mail.smtp.starttls.enable", "false"));
		htmlEmail.setStartTLSEnabled(startTls);
		htmlEmail.setStartTLSRequired(startTls);

		if (Boolean.parseBoolean(mailProperties.getProperty("mail.smtp.debug", "false"))) {
			htmlEmail.setDebug(true);
		}

		return htmlEmail;
	}

	@Override
	public Key getKey() {
		try {
			return txService.getOrCreateCookieEncryptionKey();
		}
		catch (CryptoException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Hash getHash() {
		return new DefaultHash().setAlgorithm("SHA-512").setIterations(20000).setSaltLength(96);
	}

	private String getHashedUsername(final String username) {
		return new DefaultHash().setSaltLength(0).setIterations(1).generate(username);
	}

	private void writeExtJsField(final JsonGenerator g, final ExtJsFieldDto field) throws IOException {
		g.writeObject(field);
		g.writeRaw(",");
	}
}
