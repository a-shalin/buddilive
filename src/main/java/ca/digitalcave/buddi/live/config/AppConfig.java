package ca.digitalcave.buddi.live.config;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonFactory;

import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.Algorithm;
import ca.digitalcave.moss.restlet.util.PasswordChecker;

@Configuration
public class AppConfig {

	@Bean
	public JsonFactory jsonFactory() {
		return new JsonFactory();
	}

	@Bean
	public Crypto crypto() {
		return new Crypto().setAlgorithm(Algorithm.AES_256).setSaltLength(32).setKeyIterations(1);
	}

	@Bean
	public PasswordChecker passwordChecker() {
		return new PasswordChecker().setHistoryEnforced(false);
	}

	@Bean
	public Properties mailProperties(
			@Value("${buddi.mail.smtp.from:}") String from,
			@Value("${buddi.mail.smtp.host:}") String host,
			@Value("${buddi.mail.smtp.port:25}") String port,
			@Value("${buddi.mail.smtp.auth:false}") String auth,
			@Value("${buddi.mail.smtp.username:}") String username,
			@Value("${buddi.mail.smtp.password:}") String password,
			@Value("${buddi.mail.smtp.starttls.enable:false}") String starttls) {
		final Properties props = new Properties();
		props.setProperty("mail.smtp.from", from);
		props.setProperty("mail.smtp.host", host);
		props.setProperty("mail.smtp.port", port);
		props.setProperty("mail.smtp.auth", auth);
		props.setProperty("mail.smtp.username", username);
		props.setProperty("mail.smtp.password", password);
		props.setProperty("mail.smtp.starttls.enable", starttls);
		props.setProperty("mail.subject", "BuddiLive Account Activation");
		return props;
	}
}
