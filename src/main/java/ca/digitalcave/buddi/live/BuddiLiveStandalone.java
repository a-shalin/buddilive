package ca.digitalcave.buddi.live;

import java.io.File;
import java.net.URL;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class BuddiLiveStandalone {

	public static void main(final String[] args) throws Exception {
		final Server server = new Server(8686);
		final String war = findWar();
		final WebAppContext context = new WebAppContext(war, "/buddilive");

		context.setClassLoader(BuddiLiveStandalone.class.getClassLoader());
		server.setHandler(context);

		server.start();
		server.join();
	}

	private static String findWar() {
		// Set by WarBootstrap when launched via java -jar
		final String warPath = System.getProperty("buddilive.war");
		if (warPath != null) {
			return warPath;
		}

		final URL location = BuddiLiveStandalone.class.getProtectionDomain().getCodeSource().getLocation();
		final File file = new File(location.getPath());

		// Running from a .war file directly
		if (file.isFile() && file.getName().endsWith(".war")) {
			return file.getAbsolutePath();
		}

		// Running via mvn exec:java — exploded WAR in target/<finalName>
		final File exploded = new File("target/buddilive");
		if (exploded.isDirectory()) {
			return exploded.getAbsolutePath();
		}

		// Fallback: source webapp for development
		final File srcWebapp = new File("src/main/webapp");
		if (srcWebapp.isDirectory()) {
			return srcWebapp.getAbsolutePath();
		}

		throw new IllegalStateException("Cannot find webapp directory or WAR file");
	}
}
