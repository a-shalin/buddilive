package ca.digitalcave.moss.crypto;

import java.io.UnsupportedEncodingException;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A utility class to simplify the Java encryption API.  This gives you the following abilities:
 *  1) Generate fully encapsulated cipher text for pre-existing key
 * 		Cipher text Format: "algorithmId:IV:cipherText"
 *  2) Generate fully encapsulated cipher text for keys generated from a password
 * 		Cipher text Format: "iterations:salt:algorithmId:IV:cipherText"
 *  3) Generate fully encapsulated key spec from a password
 * 		Format: "iterations:salt:algorithmId
 *  3) Generate keys from a password
 *  4) Decrypt the encapsulated cipher text using either password or key (depending on which of the encryption methods was used)
 * 
 * @author wyatt
 *
 */
public class Crypto {
	private String rngAlgorithm = "SHA1PRNG";
	private Algorithm algorithm = Algorithm.AES_128;
	private int saltLength = 16;
	private int keyIterations = 1;
	
	public static void main(String[] args) throws Exception {
		Crypto crypto = new Crypto();
		crypto.setAlgorithm(Algorithm.AES_256);
		
		//Test PBE Keys
		final PBEKeySpec pbeKeySpec = crypto.generatePBEKeySpec("password1");
		final String pbeKeySpecEncoded = crypto.encodePBEKeySpec(pbeKeySpec);
		System.out.println(pbeKeySpecEncoded + " - PBE Key Spec");
		final SecretKey pbeKey = (SecretKey) crypto.generatePBEKey(pbeKeySpec);
		final SecretKey pbeKey2 = (SecretKey) recoverPBEKey(pbeKeySpecEncoded, "password1");
		System.out.println(Base64.encode(pbeKey.getEncoded()) + " - Encoded PBE Key");
		System.out.println(Base64.encode(pbeKey2.getEncoded()) + " - Recovered PBE Key (should match above line)");
		System.out.println(decrypt(pbeKey2, crypto.encrypt(pbeKey, "plaintext")));
		
		//Test password encryption (on-the-fly PBE key generation)
		System.out.println(crypto.encrypt("password1", "plaintext") + " - On-the-fly PBE Encryption, with encoded keyspec");
		System.out.println(decrypt("password1", crypto.encrypt("password1", "plaintext")));
		
		//Test random keys
		final SecretKey secretKey = crypto.generateSecretKey();
		final String secretKeyEncoded = encodeSecretKey(secretKey);
		System.out.println(secretKeyEncoded + " - Secret Key");
		final SecretKey secretKey2 = recoverSecretKey(secretKeyEncoded);
		System.out.println(Base64.encode(secretKey.getEncoded()) + " - Encoded Secret Key");
		System.out.println(Base64.encode(secretKey2.getEncoded()) + " - Recovered Secret Key (should match above line)");
		System.out.println(crypto.encrypt(secretKey, "plaintext"));
		System.out.println(decrypt(secretKey2, crypto.encrypt(secretKey, "plaintext")));
	}

	//*********************** Secret Key methods ***********************
	/**
	 * Generates a random SecretKey using the keyAlgorithm specified in Crypto.algorithm.
	 * @return
	 * @throws CryptoException
	 */
	public SecretKey generateSecretKey() throws CryptoException {
		try {
			KeyGenerator generator = KeyGenerator.getInstance(algorithm.keyAlgorithm);
			generator.init(algorithm.keyLength);
			return generator.generateKey();
		}
		catch (NoSuchAlgorithmException e){
			throw new CryptoException(e);
		}
	}
	/**
	 * Encodes a secret key using the currently specified algorithm details,
	 * in the form
	 * {algorithm}:{base64encodedKey}
	 * @return
	 * @throws CryptoException
	 */
	public static String encodeSecretKey(SecretKey key) {
		return key.getAlgorithm() + ":" + Base64.encode(key.getEncoded());
	}
	/**
	 * Recover a previously encoded SecretKeySpec.
	 * @param encoded
	 * @return
	 * @throws CryptoException
	 */
	public static SecretKey recoverSecretKey(String encoded) throws CryptoException {
		if (encoded == null) throw new CryptoException("Invalid secret key");
		final String[] split = encoded.split(":");
		if (split.length != 2) throw new CryptoException("Invalid secret key");
		final String algorithm = split[0];
		final byte[] decoded = Base64.decode(split[1]);
		return new SecretKeySpec(decoded, algorithm);
	}

	
	//*********************** PBE Key methods ***********************
	
	/**
	 * Generate a new PBE KeySpec, using parameters defined in the specified algorithm, and random salt.
	 * @param password
	 * @return
	 */
	public PBEKeySpec generatePBEKeySpec(String password) {
		return new PBEKeySpec(password.toCharArray(), getRandomSalt(), keyIterations, algorithm.keyLength);
	}
	/**
	 * Encode the specified PBEKeySpec using the form:
	 * {algorithmId}:{iterations}:{salt}
	 * This MUST be called using the same Crypto instance that created the supplied PBEKeySpec (or at least with the same algorithm settings).
	 * @param keySpec
	 * @return
	 */
	public String encodePBEKeySpec(PBEKeySpec keySpec) {
		final StringBuilder sb = new StringBuilder();
		sb.append(algorithm.id);
		sb.append(":");
		sb.append(keySpec.getIterationCount());
		sb.append(":");
		sb.append(Base64.encode(keySpec.getSalt()));
		return sb.toString();
	}
	/**
	 * Recover a previously encoded PBEKeySpec.  The supplied password MUST be the same as
	 * the one originally supplied when generating the keyspec.
	 * @param encoded
	 * @param password
	 * @return
	 * @throws CryptoException
	 */
	public static PBEKeySpec recoverPBEKeySpec(String encoded, String password) throws CryptoException {
		if (encoded == null) throw new CryptoException("Invalid PBE key");
		final String[] split = encoded.split(":");
		final Algorithm algorithm = Algorithm.findById(Integer.parseInt(split[0]));
		final int iterations = Integer.parseInt(split[1]);
		final byte[] salt = Base64.decode(split[2]);
		return new PBEKeySpec(password.toCharArray(), salt, iterations, algorithm.keyLength);
	}
	/**
	 * Generates a key, using the supplied keyspec.  This MUST be called using a Crypto instance with the 
	 * same algorithm and options specified as the one which created the supplied keyspec.
	 * @param keySpec
	 * @return
	 * @throws CryptoException
	 */
	public SecretKey generatePBEKey(PBEKeySpec keySpec) throws CryptoException {
		try {
			final SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(algorithm.keyFactoryAlgorithm);
			final Key tmp = keyFactory.generateSecret(keySpec);
			return new SecretKeySpec(tmp.getEncoded(), algorithm.keyAlgorithm);
		} catch (InvalidKeySpecException e) {
			throw new CryptoException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new CryptoException(e);
		}
	}
	/**
	 * Recover a Key from a previously encoded PBEKeySpec.
	 * @param encoded
	 * @param password
	 * @return
	 * @throws CryptoException
	 */
	public static SecretKey recoverPBEKey(String encoded, String password) throws CryptoException {
		final String[] split = encoded.split(":");
		final Crypto crypto = new Crypto().setAlgorithm(Algorithm.findById(Integer.parseInt(split[0])));
		return crypto.generatePBEKey(recoverPBEKeySpec(encoded, password));
	}
	
	
	//*********************** Encrypt / Decrypt methods ***********************
	
	/**
	 * Encrypts a value using an existing Key object.  The encrypted form includes the IV and the 
	 * encrypted value, as an encoded string, with colons separating the parts.
	 * @param key
	 * @param plainText
	 * @return
	 * @throws CryptoException
	 */
	public String encrypt(Key key, String plainText) throws CryptoException {
		if (plainText == null) return null;
		try {
			return encryptBytes(key, plainText.getBytes("UTF-8"));
		}
		catch (UnsupportedEncodingException e){
			throw new CryptoException(e);
		}
	}
	
	/**
	 * Encrypts a byte array using an existing Key object.  The encrypted form includes the IV and the 
	 * encrypted value, as an encoded string, with colons separating the parts.
	 * @param key
	 * @param plainText
	 * @return
	 * @throws CryptoException
	 */
	public String encryptBytes(Key key, byte[] plainText) throws CryptoException {
		if (plainText == null) return null;
		try {
			final Cipher c = Cipher.getInstance(algorithm.cipherAlgorithm);
			c.init(Cipher.ENCRYPT_MODE, key);
			final AlgorithmParameters p = c.getParameters();

			final byte[] iv = p == null ? new byte[0] : p.getParameterSpec(IvParameterSpec.class).getIV();
			final byte[] out = c.doFinal(plainText);

			final StringBuilder sb = new StringBuilder();
			sb.append(algorithm.id);
			sb.append(":");
			sb.append(Base64.encode(iv));
			sb.append(":");
			sb.append(Base64.encode(out));
			
			return sb.toString();
		}
		catch (Exception e){
			throw new CryptoException(e);
		}
	}
	
	/**
	 * Encrypts a value using a password.  The encrypted form includes the Algorithm, key length, 
	 * iteration count, salt, IV, and the encrypted value, as an encoded string, with colons 
	 * separating the parts.
	 * @param password
	 * @param plainText
	 * @return
	 * @throws CryptoException
	 */
	public String encrypt(String password, String plainText) throws CryptoException {
		if (plainText == null) return null;
		try {
			final PBEKeySpec keySpec = generatePBEKeySpec(password);
			final Key key = generatePBEKey(keySpec);
			
			final String encrypted = encrypt(key, plainText);
			final String[] split = encrypted.split(":", 2);
			final StringBuilder sb = new StringBuilder();
			sb.append(split[0]);	//The algorithm ID
			sb.append(":");
			sb.append(keySpec.getIterationCount());
			sb.append(":");
			sb.append(Base64.encode(keySpec.getSalt()));
			sb.append(":");
			sb.append(split[1]);	//The remainder of encrypted value: IV and ciphertext
			
			return sb.toString();
		}
		catch (Exception e){
			throw new CryptoException(e);
		}
	}

	public static String decrypt(Key key, String encrypted) throws CryptoException {
		if (encrypted == null) return null;
		try {
			return new String(decryptBytes(key, encrypted), "UTF-8");
		}
		catch (UnsupportedEncodingException e) {
			throw new CryptoException(e);
		}
	}
	
	public static byte[] decryptBytes(Key key, String encrypted) throws CryptoException {
		if (encrypted == null) return null;
		String[] split = encrypted.split(":");
		if (split.length != 3) {
			throw new CryptoException("Invalid cyphertext");
		}

		try {
			final Algorithm algorithm = Algorithm.findById(Integer.parseInt(split[0]));
			final byte[] iv = Base64.decode(split[1]);
			final byte[] in = Base64.decode(split[2]);
			
			final Cipher c = Cipher.getInstance(algorithm.cipherAlgorithm);
			c.init(Cipher.DECRYPT_MODE, key, iv.length == 0 ? null : new IvParameterSpec(iv));
			return c.doFinal(in);
		}
		catch (Throwable e){
			throw new CryptoException(e);
		}
	}
	
	public static String decrypt(String password, String value) throws CryptoException {
		if (value == null) return null;
		String[] split = value.split(":");
		if (split.length == 5) {
			final Algorithm algorithm = Algorithm.findById(Integer.parseInt(split[0]));

			// recover the iv
			final String iv = split[3];

			// recover the cyphertext
			final String in = split[4];

			try {
				final PBEKeySpec keySpec = recoverPBEKeySpec(split[0] + ":" + split[1] + ":" + split[2], password);
				Crypto crypto = new Crypto().setAlgorithm(algorithm);
				return decrypt(crypto.generatePBEKey(keySpec), algorithm.id + ":" + iv + ":" + in);
			}
			catch (Exception e){
				throw new CryptoException(e);
			}
		}
		else if (split.length == 4){
			//Backwards compatibility for legacy Buddi Live encryption.  Eventually this will be deleted.

			// recover the salt
			final int iterations = Integer.parseInt(split[0]);

			// recover the salt
			final byte[] salt = Base64.decode(split[1]);

			// recover the iv
			final byte[] iv = Base64.decode(split[2]);

			// recover the cyphertext
			final byte[] in = Base64.decode(split[3]);

			try {
				final SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
				final PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
				final Key tmp = keyFactory.generateSecret(keySpec);
				final Key key = new SecretKeySpec(tmp.getEncoded(), "AES");

				final Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
				c.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
				return new String(c.doFinal(in), "UTF-8");
			}
			catch (Exception e){
				throw new CryptoException(e);
			}
		}
		throw new CryptoException("Invalid cyphertext");
	}

	//*********************** Helper methods ***********************
	
	private byte[] getRandomSalt() {
		if (saltLength == 0){
			//Salt length for PBEKeys cannot be zero, so supply a constant salt in this case.
			return new byte[]{0x00};
		}
		return getRandomBytes(saltLength);
	}

	public byte[] getRandomBytes(int length) {
		final byte[] result = new byte[length];
		try {
			final SecureRandom r = SecureRandom.getInstance(rngAlgorithm);
			r.nextBytes(result);
			return result;
		}
		catch (NoSuchAlgorithmException e){
			throw new RuntimeException(e);
		}
	}


	//*********************** Getter / Setter methods ***********************


	public String getRngAlgorithm() {
		return rngAlgorithm;
	}

	public Crypto setRngAlgorithm(String rngAlgorithm) {
		this.rngAlgorithm = rngAlgorithm;
		return this;
	}

	public Algorithm getAlgorithm() {
		return algorithm;
	}
	
	public Crypto setAlgorithm(Algorithm algorithm) {
		this.algorithm = algorithm;
		return this;
	}

	public int getSaltLength() {
		return saltLength;
	}

	public Crypto setSaltLength(int saltLength) {
		this.saltLength = saltLength;
		return this;
	}

	public int getKeyIterations() {
		return keyIterations;
	}

	public Crypto setKeyIterations(int iterations) {
		this.keyIterations = iterations;
		return this;
	}

	public enum Algorithm {
		AES_128(0, "PBKDF2WithHmacSHA1", "AES", "AES/CBC/PKCS5Padding", 128),
		AES_256(1, "PBKDF2WithHmacSHA1", "AES", "AES/CBC/PKCS5Padding", 256),
		DES_56(2, "PBEWithMD5AndDES", "DES", "DES/CBC/PKCS5Padding", 56),			//The password for the keyspec must be exactly 8 characters long for this algorithm
		DESede_168(3, "PBEWithSHA1AndDESede", "DESede", "DESede/CBC/PKCS5Padding", 168);

		public final int id;
		public final String keyFactoryAlgorithm;
		public final String keyAlgorithm;
		public final String cipherAlgorithm;
		public final int keyLength;
		
		private Algorithm(int id, String keyFactoryAlgorithm, String keyAlgorithm, String cipherAlgorithm, int keyLength) {
			this.id = id;
			this.keyFactoryAlgorithm = keyFactoryAlgorithm;
			this.keyAlgorithm = keyAlgorithm;
			this.cipherAlgorithm = cipherAlgorithm;
			this.keyLength = keyLength;
		}
		
		public static Algorithm findById(int id) throws CryptoException {
			for (Algorithm algorithm : Algorithm.values()) {
				if (id == algorithm.id) return algorithm;
			}
			throw new CryptoException("Unknown algorithm ID " + id);
		}
	}
	
	public static class CryptoException extends Exception {
		private static final long serialVersionUID = 1L;
		public CryptoException() {}
		public CryptoException(String message){
			super(message);
		}
		public CryptoException(Throwable throwable){
			super(throwable);
		}
		public CryptoException(String message, Throwable throwable){
			super(message, throwable);
		}
	}
}
