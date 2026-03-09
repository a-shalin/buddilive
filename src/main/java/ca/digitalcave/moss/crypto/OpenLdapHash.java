package ca.digitalcave.moss.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class OpenLdapHash extends Hash {
	
	private Algorithm algorithm = Algorithm.SSHA;

	protected String generate(byte[] salt, String message) {
		try {
			final MessageDigest digest = MessageDigest.getInstance(algorithm.hashAlgorithm);
			digest.update(message.getBytes());
			if (algorithm.salted) digest.update(salt);
			final byte[] hash = digest.digest();
			final byte[] result = new byte[hash.length + (algorithm.salted ? salt.length : 0)];
			System.arraycopy(hash, 0, result, 0, hash.length);
			if (algorithm.salted) System.arraycopy(salt, 0, result, hash.length, salt.length);
			return "{" + algorithm.name().toLowerCase() + "}" + Base64.encode(result, false);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean verify(String hash, String message) {
		if (hash == null) return false;
		final String[] split = hash.replaceFirst("\\{", "").split("\\}", 2);
		if (split.length != 2) return false;
		final Algorithm algorithm = Algorithm.valueOf(split[0].toUpperCase());
		byte[] rawBytes = Base64.decode(split[1]);
		byte[] hashBytes = new byte[algorithm.hashLength];
		byte[] salt = new byte[rawBytes.length - hashBytes.length];
		System.arraycopy(rawBytes, 0, hashBytes, 0, hashBytes.length);
		System.arraycopy(rawBytes, hashBytes.length, salt, 0, salt.length);
		return hash.equals(new OpenLdapHash().setAlgorithm(algorithm).generate(salt, message));
	}

	public Algorithm getAlgorithm() {
		return algorithm;
	}
	public OpenLdapHash setAlgorithm(Algorithm algorithm) {
		this.algorithm = algorithm;
		return this;
	}
	
	public enum Algorithm {
		MD5("MD5", false, 16),
		SMD5("MD5", true, 16),
		SHA("SHA-1", false, 20),
		SSHA("SHA-1", true, 20);

		public final String hashAlgorithm;
		public final boolean salted;
		public final int hashLength;
		
		private Algorithm(String hashAlgorithm, boolean salted, int hashLength) {
			this.hashAlgorithm = hashAlgorithm;
			this.salted = salted;
			this.hashLength = hashLength;
		}
	}
}
