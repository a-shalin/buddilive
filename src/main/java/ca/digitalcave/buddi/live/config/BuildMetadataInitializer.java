package ca.digitalcave.buddi.live.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class BuildMetadataInitializer {

	private static final String VERSION_PROPERTIES_PATH = "version.properties";
	private static final String BUILD_DATE_PROPERTY = "BUILD_DATE";
	private static final String VERSION_PROPERTY = "VERSION";

	@PostConstruct
	public void initialize() {
		final Properties properties = new Properties();

		try (InputStream inputStream = new ClassPathResource(VERSION_PROPERTIES_PATH).getInputStream()) {
			properties.load(inputStream);
		}
		catch (IOException e) {
			return;
		}

		setSystemPropertyIfMissing(properties, BUILD_DATE_PROPERTY);
		setSystemPropertyIfMissing(properties, VERSION_PROPERTY);
	}

	private void setSystemPropertyIfMissing(final Properties properties, final String key) {
		final String existingValue = System.getProperty(key);
		if (existingValue != null && !existingValue.isBlank()) {
			return;
		}

		final String value = properties.getProperty(key);
		if (value != null && !value.isBlank()) {
			System.setProperty(key, value.trim());
		}
	}
}
