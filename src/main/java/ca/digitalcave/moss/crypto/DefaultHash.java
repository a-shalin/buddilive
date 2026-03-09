package ca.digitalcave.moss.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DefaultHash extends Hash {
	
	private String algorithm = "SHA-256";
		
	protected String generate(byte[] salt, String message) {
		try {
			final MessageDigest digest = MessageDigest.getInstance(algorithm);
			final byte[] messageBytes = message.getBytes();
			
			digest.update(messageBytes);
			digest.update(salt);

			for (int i = 0; i < getIterations(); i++) {
				digest.update(digest.digest());
				digest.update(messageBytes);
				digest.update(salt);
			}

			final StringBuilder sb = new StringBuilder();
			sb.append(algorithm);
			sb.append(":");
			sb.append(Integer.toString(getIterations(), 16));
			sb.append(":");
			sb.append(Base64.encode(salt));
			sb.append(":");
			sb.append(Base64.encode(digest.digest()));
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean verify(String hash, String message) {
		if (hash == null) return false;
		final int a = hash.indexOf(':');
		final int b = hash.indexOf(':', a + 1);
		final int c = hash.indexOf(':', b + 1);
		final String algorithm = hash.substring(0, a);
		final int iterations = Integer.parseInt(hash.substring(a + 1, b), 16);
		final byte[] salt = Base64.decode(hash.substring(b + 1, c));
		final String calc = new DefaultHash().setAlgorithm(algorithm).setIterations(iterations).generate(salt, message);
		return hash.equals(calc);
	}
	
	public String getAlgorithm() {
		return algorithm;
	}
	public DefaultHash setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
		return this;
	}

}
