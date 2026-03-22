import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Bootstraps the standalone server from an executable WAR.
 * Extracts WEB-INF/classes and WEB-INF/lib to a temp directory,
 * builds a classloader, and invokes BuddiLiveStandalone.main().
 */
public class WarBootstrap {

	public static void main(final String[] args) throws Exception {
		final File warFile = new File(
				WarBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		if (!warFile.isFile()) {
			System.err.println("WarBootstrap must be run from a WAR file: java -jar buddilive.war");
			System.exit(1);
		}

		final File tempDir = new File(System.getProperty("java.io.tmpdir"),
				"buddilive-" + System.currentTimeMillis());
		tempDir.mkdirs();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursive(tempDir)));

		final List<URL> urls = new ArrayList<>();

		try (final JarFile jar = new JarFile(warFile)) {
			final Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				final JarEntry entry = entries.nextElement();
				final String name = entry.getName();
				if (name.startsWith("WEB-INF/classes/") || name.startsWith("WEB-INF/lib/")) {
					final File dest = new File(tempDir, name);
					if (entry.isDirectory()) {
						dest.mkdirs();
					} else {
						dest.getParentFile().mkdirs();
						try (final InputStream in = jar.getInputStream(entry);
								final FileOutputStream out = new FileOutputStream(dest)) {
							final byte[] buf = new byte[8192];
							int n;
							while ((n = in.read(buf)) != -1) {
								out.write(buf, 0, n);
							}
						}
						if (name.startsWith("WEB-INF/lib/") && name.endsWith(".jar")) {
							urls.add(dest.toURI().toURL());
						}
					}
				}
			}
		}

		urls.add(0, new File(tempDir, "WEB-INF/classes").toURI().toURL());

		System.setProperty("buddilive.war", warFile.getAbsolutePath());

		final URLClassLoader loader = new URLClassLoader(
				urls.toArray(new URL[0]), WarBootstrap.class.getClassLoader());
		Thread.currentThread().setContextClassLoader(loader);

		final Class<?> mainClass = loader.loadClass("ca.digitalcave.buddi.live.BuddiLiveStandalone");
		final Method main = mainClass.getMethod("main", String[].class);
		main.invoke(null, (Object) args);
	}

	private static void deleteRecursive(final File file) {
		if (file.isDirectory()) {
			final File[] children = file.listFiles();
			if (children != null) {
				for (final File child : children) {
					deleteRecursive(child);
				}
			}
		}
		file.delete();
	}
}
