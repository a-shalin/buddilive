package ca.digitalcave.buddi.live.config;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

@Component
public class AssetVersionTokenProvider {

	private static final String BUILD_DATE_PROPERTY = "BUILD_DATE";
	private static final String BUILD_DATE_PATTERN = "yyyy-MM-dd HH:mm";
	private static final DateTimeFormatter BUILD_DATE_FORMATTER = DateTimeFormatter.ofPattern(BUILD_DATE_PATTERN);

	private final String token;

	public AssetVersionTokenProvider() {
		final long startupMillis = System.currentTimeMillis();
		this.token = resolveToken(startupMillis);
	}

	public String getToken() {
		return token;
	}

	private String resolveToken(final long fallbackMillis) {
		final String buildDate = System.getProperty(BUILD_DATE_PROPERTY);
		if (buildDate == null || buildDate.isBlank()) {
			return String.valueOf(fallbackMillis);
		}

		try {
			final LocalDateTime dateTime = LocalDateTime.parse(buildDate, BUILD_DATE_FORMATTER);
			return String.valueOf(dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
		}
		catch (DateTimeParseException e) {
			return String.valueOf(fallbackMillis);
		}
	}
}
