package ca.digitalcave.buddi.live.e2e;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import ca.digitalcave.buddi.live.BuddiSpringApplication;

public abstract class BaseIT {

	private static int port;
	private static GreenMail greenMail;
	private static boolean started = false;
	private static ConfigurableApplicationContext context;

	@BeforeAll
	static void ensureServerStarted() throws Exception {
		if (started) return;
		started = true;

		final Path derbyDir = Path.of("target/e2etest-derby");
		if (Files.exists(derbyDir)) {
			Files.walk(derbyDir)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
		}

		greenMail = new GreenMail(new ServerSetup(8025, "localhost", ServerSetup.PROTOCOL_SMTP));
		greenMail.start();

		final SpringApplication app = new SpringApplication(BuddiSpringApplication.class);
		context = app.run(
			"--spring.profiles.active=e2etest",
			"--server.port=0"
		);
		port = context.getEnvironment().getProperty("local.server.port", Integer.class);

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try { if (context != null) context.close(); } catch (Exception ignored) {}
			try { if (greenMail != null) greenMail.stop(); } catch (Exception ignored) {}
		}));
	}

	protected static String getBaseUrl() {
		return "http://localhost:" + port;
	}

	protected static String getDbUrl() {
		return "jdbc:derby:directory:target/e2etest-derby";
	}
}
