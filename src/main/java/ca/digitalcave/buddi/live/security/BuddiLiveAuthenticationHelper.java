package ca.digitalcave.buddi.live.security;

import ca.digitalcave.buddi.live.db.BuddiSystem;
import ca.digitalcave.buddi.live.db.Users;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.auth.config.AuthenticationConfiguration;
import ca.digitalcave.moss.auth.model.AuthUser;
import ca.digitalcave.moss.auth.service.AuthenticationHelper;
import ca.digitalcave.moss.auth.template.ExtraFieldsDirective;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.Algorithm;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import ca.digitalcave.moss.crypto.DefaultHash;
import ca.digitalcave.moss.crypto.Hash;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail2.core.EmailException;
import org.apache.commons.mail2.jakarta.HtmlEmail;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.Writer;
import java.security.Key;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class BuddiLiveAuthenticationHelper extends AuthenticationHelper {

	private final Users users;
	private final BuddiSystem buddiSystem;
	private final Properties mailProperties;

	public BuddiLiveAuthenticationHelper(final Users users,
											 final BuddiSystem buddiSystem,
											 final Properties mailProperties,
											 @org.springframework.beans.factory.annotation.Value("${buddi.directRegistration:false}") final boolean directRegistration) {
		super(new AuthenticationConfiguration());
		this.users = users;
		this.buddiSystem = buddiSystem;
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
					final JsonGenerator g = new JsonFactory().createGenerator(out);
					g.writeStartObject();
					g.writeStringField("xtype", "selfdocumentingfield");
					g.writeStringField("messageBody", translation.getString("HELP_LOCALE"));
					g.writeStringField("type", "localescombobox");
					g.writeStringField("name", "locale");
					g.writeStringField("fieldLabel", translation.getString("LOCALE"));
					g.writeStringField("value", "en_US");
					g.writeEndObject();
					g.writeRaw(",");
					g.writeStartObject();
					g.writeStringField("xtype", "selfdocumentingfield");
					g.writeStringField("messageBody", translation.getString("HELP_CURRENCY"));
					g.writeStringField("type", "currenciescombobox");
					g.writeStringField("name", "currency");
					g.writeStringField("fieldLabel", translation.getString("CURRENCY"));
					g.writeStringField("value", "USD");
					g.writeEndObject();
					g.writeRaw(",");
					g.writeStartObject();
					g.writeStringField("xtype", "selfdocumentingfield");
					g.writeStringField("messageBody", translation.getString("CREATE_USER_AGREEMENT_REQUIRED"));
					g.writeStringField("type", "checkbox");
					g.writeStringField("boxLabel", translation.getString("AGREE_TERMS_AND_CONDITIONS"));
					g.writeStringField("name", "agree");
					g.writeStringField("fieldLabel", " ");
					g.writeStringField("labelSeparator", "");
					g.writeEndObject();
					g.writeRaw(",");
					g.writeStartObject();
					g.writeStringField("xtype", "label");
					g.writeStringField("html", translation.getString(directRegistration ? "HELP_REGISTER_DIRECT" : "HELP_REGISTER"));
					g.writeEndObject();
					g.writeRaw(",");
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
					final JsonGenerator g = new JsonFactory().createGenerator(out);
					g.writeStartObject();
					g.writeStringField("xtype", "label");
					g.writeStringField("html", translation.getString("HELP_REGISTER_2"));
					g.writeEndObject();
					g.writeRaw(",");
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
					final JsonGenerator g = new JsonFactory().createGenerator(out);
					g.writeStartObject();
					g.writeStringField("xtype", "label");
					g.writeStringField("html", translation.getString("HELP_RESET_PASSWORD"));
					g.writeEndObject();
					g.writeRaw(",");
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
					final JsonGenerator g = new JsonFactory().createGenerator(out);
					g.writeStartObject();
					g.writeStringField("xtype", "label");
					g.writeStringField("html", translation.getString("HELP_RESET_PASSWORD_2"));
					g.writeEndObject();
					g.writeRaw(",");
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

		final User user = users.selectUser(getHashedUsername(identifier));
		if (user == null) {
			return null;
		}

		boolean authenticated = false;

		final String storedSecret = new String(user.getSecret());

		boolean legacy = false;

		if (storedSecret.startsWith("SHA-512:")) {
			authenticated = DefaultHash.verify(storedSecret, secret);
		}
		else if (storedSecret.startsWith("SHA-256:")) {
			authenticated = DefaultHash.verify(storedSecret, secret);
			legacy = true;
		}
		else {
			authenticated = false;
		}

		if (authenticated) {
			if (legacy) {
				try {
					users.updateUserSecret(user, getHash().generate(secret));
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
	@Transactional(readOnly = true)
	public AuthUser selectUser(final String username) {
		return users.selectUser(getHashedUsername(username));
	}

	@Override
	public List<AuthUser> selectUsers(final String email) {
		throw new RuntimeException("Forgot Username not implemented");
	}

	@Transactional
	public boolean insertTotpSecret(final String username, final String totpSharedSecret) {
		final User user = users.selectUser(getHashedUsername(username));
		if (user != null) {
			users.deleteUnusedBackupCodes(user);
			final int count = users.updateUserTotpSecret(user, totpSharedSecret);
			if (count == 1) {
				return true;
			}
		}

		markCurrentTransactionForRollback();
		return false;
	}

	@Override
	@Transactional
	public void insertTotpBackupCodes(final String username) {
		final User user = users.selectUser(getHashedUsername(username));
		if (user != null) {
			users.deleteUnusedBackupCodes(user);
			for (int i = 0; i < 10; i++) {
				final String backupCode = UUID.randomUUID().toString();
				users.insertTotpBackupCode(user, backupCode);
			}
			return;
		}

		markCurrentTransactionForRollback();
	}

	@Override
	@Transactional
	public void updateTotpBackupCodeMarkUsed(final String username, final String backupCode) {
		final User user = users.selectUser(getHashedUsername(username));
		if (user != null) {
			final int count = users.updateUserTotpBackupCodeUsed(user, backupCode);
			if (count == 1) {
				return;
			}
		}

		markCurrentTransactionForRollback();
	}

	@Override
	@Transactional
	public void disableTotp(final String username) {
		final User user = users.selectUser(getHashedUsername(username));
		if (user != null && StringUtils.isBlank(user.getTwoFactorSecret())) {
			user.setTwoFactorRequired(false);
			final int count = users.updateUser(user);
			if (count == 1) {
				users.updateUserTotpSecret(user, null);
				users.deleteUnusedBackupCodes(user);
				return;
			}
		}

		markCurrentTransactionForRollback();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String updateActivationKey(final String username, final String activationKey) throws Exception {
		try {
			final String hashedIdentifier = getHashedUsername(username);
			final User user = users.selectUser(hashedIdentifier);
			if (user == null) throw new DatabaseException("Could not find user with hashed identifier" + hashedIdentifier);
			if (user.isEncrypted()) throw new DatabaseException("Users with encrypted data cannot reset passwords.");

			cleanupUsers(user);

			final Integer count = users.insertActivationKey(user, activationKey);
			if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));

			return username;
		}
		catch (DatabaseException e) {
			Logger.getLogger(this.getClass().getName()).log(Level.INFO, e.getMessage());
			markCurrentTransactionForRollback();
		}
		return null;
	}

	@Override
	@Transactional
	public boolean updatePasswordByActivationKey(final String activationKey, final String hashedPassword) {
		final User user = users.selectUserByActivationKey(activationKey);
		if (user == null) {
			throw new DatabaseException("Activation key is not valid");
		}
		final Integer count = users.updateUserSecret(user, hashedPassword);
		if (count != 1) {
			throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
		}

		cleanupUsers(user);
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void insertUser(final String email, final String activationKey, final Map<String, String> formParams) throws Exception {
		if (!"on".equals(formParams.getOrDefault("agree", "off"))) {
			throw new IllegalArgumentException(LocaleUtil.getTranslation().getString("CREATE_USER_AGREEMENT_REQUIRED"));
		}

		final User newUser = new User();
		newUser.setIdentifier(getHashedUsername(email));
		newUser.setUuid(UUID.randomUUID().toString());
		newUser.setCurrency(Currency.getInstance(formParams.getOrDefault("currency", "USD")));
		newUser.setLocale(LocaleUtil.parseLocale(formParams.getOrDefault("locale", "en_US"), Locale.US));

		final User existingUser = users.selectUser(newUser.getIdentifier());
		if (existingUser != null) {
			if (existingUser.getSecret() != null && existingUser.getSecret().length > 0) {
				throw new DatabaseException("The user name already exists");
			}
			users.deleteActivationKey(existingUser);
			final int insertActivationCount = users.insertActivationKey(existingUser, activationKey);
			if (insertActivationCount != 1) throw new DatabaseException(String.format("Activation key insert failed; expected 1 row, returned %s", insertActivationCount));
			return;
		}

		cleanupUsers(null);

		final int insertUserCount = users.insertUser(newUser);
		if (insertUserCount != 1) throw new DatabaseException(String.format("User insert failed; expected 1 row, returned %s", insertUserCount));
		final int insertActivationCount = users.insertActivationKey(newUser, activationKey);
		if (insertActivationCount != 1) throw new DatabaseException(String.format("Activation key insert failed; expected 1 row, returned %s", insertActivationCount));
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

	private void cleanupUsers(final User user) {
		if (user == null) {
			users.deleteActivationKey();
		}
		else {
			users.deleteActivationKey(user);
		}
		users.deleteInactiveUsers();
	}

	@Override
	@Transactional
	public Key getKey() {
		try {
			SecretKey key;
			try {
				String keyEncoded = buddiSystem.selectCookieEncryptionKey();
				if (keyEncoded == null) {
					key = new Crypto().setAlgorithm(Algorithm.AES_256).generateSecretKey();
					keyEncoded = Crypto.encodeSecretKey(key);
					buddiSystem.deleteCookieEncryptionKey();
					buddiSystem.insertCookieEncryptionKey(keyEncoded);
				}
				key = Crypto.recoverSecretKey(keyEncoded);
			}
			catch (CryptoException e) {
				key = new Crypto().setAlgorithm(Algorithm.AES_256).generateSecretKey();
				String keyEncoded = Crypto.encodeSecretKey(key);
				buddiSystem.updateCookieEncryptionKey(keyEncoded);
			}
			return key;
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

	private void markCurrentTransactionForRollback() {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
		}
	}
}
