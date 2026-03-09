package ca.digitalcave.moss.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public abstract class Hash {

	private String rngAlgorithm = "SHA1PRNG";
	private int saltLength = 16;
	private int iterations = 1;

	public String generate(String message) {
		return generate(getRandomSalt(), message);
	}
	
	protected abstract String generate(byte[] salt, String message);
	
	public int getSaltLength() {
		return saltLength;
	}
	public Hash setSaltLength(int saltLength) {
		this.saltLength = saltLength;
		return this;
	}
	
	public String getRngAlgorithm() {
		return rngAlgorithm;
	}
	public Hash setRngAlgorithm(String rngAlgorithm) {
		this.rngAlgorithm = rngAlgorithm;
		return this;
	}
	
	public int getIterations() {
		return iterations;
	}
	public Hash setIterations(int iterations) {
		this.iterations = iterations;
		return this;
	}
	
	private byte[] getRandomSalt() {
		final byte[] salt = new byte[saltLength];
		try {
			final SecureRandom r = SecureRandom.getInstance(rngAlgorithm);
			r.nextBytes(salt);
			return salt;
		}
		catch (NoSuchAlgorithmException e){
			throw new RuntimeException(e);
		}
	}
}
