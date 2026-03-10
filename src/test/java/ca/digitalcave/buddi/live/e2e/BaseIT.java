package ca.digitalcave.buddi.live.e2e;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.jupiter.api.BeforeAll;

import ca.digitalcave.buddi.live.BuddiLiveStandalone;

public abstract class BaseIT {

	private static Server server;
	private static int port;
	private static boolean started = false;
	private static final Path CONFIG_TARGET = Path.of("src/main/webapp/WEB-INF/classes/config.properties");

	@BeforeAll
	static void ensureServerStarted() throws Exception {
		if (started) return;
		started = true;

		Path derbyDir = Path.of("target/e2etest-derby");
		if (Files.exists(derbyDir)) {
			Files.walk(derbyDir)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
		}

		Files.createDirectories(CONFIG_TARGET.getParent());
		Files.copy(Path.of("conf/e2etest/config.properties"), CONFIG_TARGET, StandardCopyOption.REPLACE_EXISTING);

		server = new Server(0);
		URL warUrl = new File("src/main/webapp").toURI().toURL();
		WebAppContext context = new WebAppContext(warUrl.toExternalForm(), "/buddilive");
		context.setClassLoader(BuddiLiveStandalone.class.getClassLoader());
		server.setHandler(context);
		server.start();

		port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();

		// Warm up the connection pool by making a request that triggers DB activity.
		// The Liquibase migration may leave a dead connection in the c3p0 pool;
		// this request forces c3p0 to cycle it out before the real tests begin.
		for (int i = 0; i < 3; i++) {
			try {
				HttpURLConnection conn = (HttpURLConnection) new URL(getBaseUrl() + "/stores/currencies").openConnection();
				conn.setConnectTimeout(5000);
				conn.setReadTimeout(5000);
				conn.getResponseCode();
				conn.disconnect();
			} catch (Exception ignored) {}
		}

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try { server.stop(); } catch (Exception ignored) {}
		}));
	}

	protected static String getBaseUrl() {
		return "http://localhost:" + port + "/buddilive";
	}

	protected static String getDbUrl() {
		return "jdbc:derby:directory:target/e2etest-derby";
	}
}
