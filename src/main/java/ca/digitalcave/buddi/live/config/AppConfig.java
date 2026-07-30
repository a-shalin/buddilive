package ca.digitalcave.buddi.live.config;

import ca.digitalcave.moss.auth.password.PasswordChecker;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.Algorithm;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Properties;

@Configuration
public class AppConfig {

	@Bean
	@Primary
	public JsonFactory buddiJsonFactory() {
		return new JsonFactory();
	}

	@Bean
	public ObjectMapper objectMapper(final JsonFactory buddiJsonFactory) {
		return new ObjectMapper(buddiJsonFactory);
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
			@Value("${buddi.mail.smtp.from:}") final String from,
			@Value("${buddi.mail.smtp.host:}") final String host,
			@Value("${buddi.mail.smtp.port:25}") final String port,
			@Value("${buddi.mail.smtp.auth:false}") final String auth,
			@Value("${buddi.mail.smtp.username:}") final String username,
			@Value("${buddi.mail.smtp.password:}") final String password,
			@Value("${buddi.mail.smtp.starttls.enable:false}") final String starttls) {
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
