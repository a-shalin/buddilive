/*
 * Created on Aug 20, 2007 by wyatt
 */

package ca.digitalcave.moss.crypto;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Date;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NullCipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A class which attempts to simplify the creation and reading of encrypted documents.  To
 * use this class, your document must consist of the following:
 * 
 * X bytes of a Header.  This is defined by you, and should be a unique way of 
 * identifying the document.  This can be as long or as short as you wish; it 
 * is suggested that it be as long as needed to ensure uniqueness.  Perhaps a 
 * byte array representation of your program name and version number would be
 * appropriate.  The header is not encrypted.
 * 
 * Y bytes of salt.  This is used in addition to the password to create a key
 * for the encryption process.  This is not encrypted either.
 * 
 * Z bytes of a Canary.  This is a known value to help determine if we have 
 * decrypted the file correctly.  If this value turns up correctly, we know that 
 * the password used is correct.  Like the header, it should be long enough
 * to ensure that it does not match by fluke.  Anything over 5 or 6 bytes should
 * be sufficient.  This value is encrypted, assuming we use a non-null cipher.
 * 
 * The rest of the file is now accessed.  If we are using a non-null cipher, 
 * this will be encrypted.
 * 
 * When we get the input stream from the getInputStream() method, the header has
 * already been checked, the salt has been loaded, the cipher created, and 
 * (if the password is correct), the CipherInputStream is created and returned.
 * At this point, the read pointer should be pointing to the start of your data;
 * i.e., to the index X + Y + Z.  This can allow you to read the file using whatever
 * means you desire.
 * 
 * If you specify true for isCompressData(), then we will also filter the plain text
 * data through gzip before reading.  This can result in much smaller data files,
 * although it makes debugging harder.
 * 
 * @author wyatt
 *
 */
public abstract class CryptoFactory {
	private final String cipherAlgorithm;
	private final String keyAlgorithm;
	private final String secureRandomAlgorithm;
	private final String digestAlgorithm;
	private final int keyLength = 16;	//You can use Cipher.getMaxAllowedKeyLength(), but that is not consistent across platforms. 8-(
	private final int platformKeyLength;	//Uses Cipher.getMaxAllowedKeyLength().  Kept for backwards compatibility.  Don't use this for new encryptions.
	private final int saltLength;	//This must be in a multiple of the block size
	private final int ivSpecLength = 16;  //This should be 16 bytes for AES

	//Only should be used for backwards compatibility.  All new files should use key length of 16 to be cross platform compatible.
	private boolean triedCrossPlatformKeyLengthAlready = false;
	
	/**
	 * Creates a new CipherStreamFactory with the default parameters (AES
	 * encryption for the cipher, with compatible supporting algorithms).
	 * @throws CipherException There was a problem creating the cipher.
	 * This is generally due to an invalid algorithm name for cipherAlgorithm.
	 * As this value is hardcoded in this constructor, you should never see this.
	 */
	public CryptoFactory() throws CipherException {
		this("AES/CFB8/PKCS5Padding",
				"AES",
				"SHA1PRNG",
				"SHA-512",
				256,
				16);
	}

	/**
	 * Creates a new cipher stream factory, using user-defined algorithms
	 * and key sizes.  For a list of valid names, please see
	 * http://java.sun.com/j2se/1.5.0/docs/guide/security/jce/JCERefGuide.html#AppA
	 * 
	 * @param cipherAlgorithm The cipher algorithm
	 * @param keyAlgorithm The key algorithm
	 * @param secureRandomAlgorithm The secure random algorithm (for generating salt)
	 * @param digestAlgorithm The digest algorithm (for converting password to byte[] for key)
	 * @param saltLength Length of the salt byte array.  This must be in a multiple of the block size.
	 * @throws CipherException There was a problem creating the cipher.  This is 
	 * probably due to an incorrect algorithm name for cipherAlgorithm.
	 */
	public CryptoFactory(String cipherAlgorithm, String keyAlgorithm, String secureRandomAlgorithm, String digestAlgorithm, int maxKeyLength, int saltLength) throws CipherException {
		this.cipherAlgorithm = cipherAlgorithm;
		this.keyAlgorithm = keyAlgorithm;
		this.secureRandomAlgorithm = secureRandomAlgorithm;
		this.digestAlgorithm = digestAlgorithm;
		this.saltLength = saltLength;

		try {
			platformKeyLength = Math.min(maxKeyLength / 8, Cipher.getMaxAllowedKeyLength(this.cipherAlgorithm) / 8);
		}
		catch (Exception e){
			throw new CipherException(e);
		}

	}


	public Date getTimestamp(InputStream inputStream) throws IncorrectDocumentFormatException {
		//If we don't include the date info, return null.
		if (!isSaveDate())
			return null;

		InputStream is = new BufferedInputStream(inputStream);

		try {
			//Read in the first bytes of the file, and verify file type
			if (!isHeaderCorrect(is))
				throw new IncorrectDocumentFormatException("File header did not match designated file header.");

			//Read in the time stamp, if we have specifed to include it.  We do not use it
			// in this method, but we need to read past it to get the salt, etc.
			byte[] b = new byte[8];
			is.read(b);

			is.close();

			return new Date(byteToLong(b));
		}
		catch (IOException ioe){}

		return null;
	}

	/**
	 * Returns an input stream pointing to the given file, after checking
	 * for a valid file header and password.
	 * 
	 * @param file The file to load from
	 * @param password The password to use for decryption.  Pass in null to use a null cipher (no encryption).
	 * @return
	 * @throws CipherException There was a problem relating to the cipher.  Generally this is due 
	 * to incorrect algorithm names. 
	 * @throws IncorrectPasswordException The cipher was set up properly, but the
	 * given password was incorrect.  You should probably handle this to re-prompt 
	 * the user for a new password. 
	 * @throws IncorrectDocumentFormatException The document is not the correct type.
	 * This means that the plain text document header did not match the one
	 * supplied for this file type.
	 * @throws IOException There was a problem accessing the given file.
	 */
	public InputStream getDecryptedStream(InputStream inputStream, char[] password) throws CipherException, IncorrectPasswordException, IncorrectDocumentFormatException, IOException {		
		InputStream is = new BufferedInputStream(inputStream);

		//Read in the first bytes of the file, and verify file type
		if (!isHeaderCorrect(is))
			throw new IncorrectDocumentFormatException("File header did not match designated file header.");

		//Read in the time stamp, if we have specifed to include it.  We do not use it
		// in this method, but we need to read past it to get the salt, etc.
		if (isSaveDate()){
			byte[] b = new byte[8];
			is.read(b);
		}

		//Read in the salt
		byte[] salt = new byte[saltLength];
		if (is.read(salt) != saltLength)
			throw new IncorrectDocumentFormatException("End of file reached before salt was read in.");

		//Create cipher
		Cipher cipher;
		if (password == null){
			cipher = new NullCipher();
		}
		else {
			try {
				int length;
				if (triedCrossPlatformKeyLengthAlready)
					length = platformKeyLength;
				else
					length = keyLength;
				triedCrossPlatformKeyLengthAlready = !triedCrossPlatformKeyLengthAlready;
				byte[] passwordKey = getKeyFromPassword(password, salt, length);
				SecretKeySpec key = new SecretKeySpec(passwordKey, keyAlgorithm);

				byte[] ivSpecKey = new byte[ivSpecLength];
				System.arraycopy(passwordKey, 0, ivSpecKey, 0, ivSpecKey.length);
				IvParameterSpec ivSpec = new IvParameterSpec(ivSpecKey);

				cipher = Cipher.getInstance(cipherAlgorithm);
				cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
			}
			catch (Exception e){
				throw new CipherException(e);
			}
		}

		//Create CipherInputStream
		BufferedInputStream bis = new BufferedInputStream(new CipherInputStream(is, cipher));
		
		//First we try to use gunzip on the stream.  If that fails, we will try without it. 
		try {
			bis.mark(1024);
			InputStream cis = new GZIPInputStream(bis);
			//Check canary
			if (!isCanaryDecrypted(cis))
				throw new IncorrectPasswordException("Incorrect password");			

			return cis;
		}
		catch (IOException ioe){
			//This stream may not be encrypted - try without encryption
			bis.reset();
			
			//Check canary
			if (!isCanaryDecrypted(bis))
				throw new IncorrectPasswordException("Incorrect password");
			
			return bis;
		}	
	}

	/**
	 * Returns an output stream writing to the given file.  Writes the 
	 * header and a random salt in plaintext, and then encrypts the stream.
	 * The stream is passed back, and everything else written to it is encrypted.
	 * 
	 * @param file The file to write to 
	 * @param password The password to use for encryption.  It is recommended that 
	 * you verify this before passing it to this method.  Pass in null to use a null cipher (no encryption). 
	 * @return
	 * @throws CipherException There was a problem relating to the cipher.  Generally this is due 
	 * to incorrect algorithm names. 
	 * @throws IOException There was a problem accessing the given file.
	 */
	public OutputStream getEncryptedStream(OutputStream outputStream, char[] password) throws CipherException, IOException {		
		OutputStream os = new BufferedOutputStream(outputStream);

		//Write the file header.  This helps identify file 
		// type on systems which look for file signatures 
		// to determine type, despite encryption.
		os.write(getHeader());

		//Write the timestamp, if we have specified to do so.
		if (isSaveDate())
			os.write(longToByte(new Date().getTime()));

		//Generate a random salt, and write to file.  Use an empty (char '0') 
		// salt for no encryption.  We use char 0 instead of 0x00 so that we 
		// can still round trip the data files in a text editor. 
		byte[] salt = new byte[saltLength];
		if (password == null){
			Arrays.fill(salt, (byte) '0');
		}
		else {
			try {
				SecureRandom secureRandom = SecureRandom.getInstance(secureRandomAlgorithm);
				secureRandom.nextBytes(salt);
			}
			catch (NoSuchAlgorithmException nsae){
				throw new CipherException(nsae);
			}
		}
		os.write(salt);

		//Create cipher
		Cipher cipher;

		if (password == null){
			cipher = new NullCipher();
		}
		else {
			try {
				byte[] passwordKey = getKeyFromPassword(password, salt, keyLength);
				SecretKeySpec key = new SecretKeySpec(passwordKey, keyAlgorithm);

				byte[] ivSpecKey = new byte[ivSpecLength];
				System.arraycopy(passwordKey, 0, ivSpecKey, 0, ivSpecKey.length);
				IvParameterSpec ivSpec = new IvParameterSpec(ivSpecKey);

				cipher = Cipher.getInstance(cipherAlgorithm);
				cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
			}
			catch (Exception e){
				throw new CipherException(e);
			}
		}

		//Create CipherOutputStream
		OutputStream cos;
		if (isCompressData())
			cos = new GZIPOutputStream(new CipherOutputStream(os, cipher));
		else
			cos = new CipherOutputStream(os, cipher);

		//Write canary
		cos.write(getCanary());

		return cos;
	}

	/**
	 * Returns a byte array containing an encrypted copy of the given plaintext byte array. 
	 * @param plaintext
	 * @param password
	 * @return
	 * @throws CipherException
	 */
	public byte[] getEncryptedBytes(final byte[] plaintext, final char[] password) throws CipherException {
		
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			OutputStream os =  getEncryptedStream(baos, password);
			os.write(plaintext);
			os.flush();
			os.close();

			return baos.toByteArray();
		}
		catch (IOException ioe){
			throw new CipherException(ioe);
		}
	}
	
	/**
	 * Returns a byte array containing an decrypted copy of the given ciphertext byte array.
	 * @param ciphertext
	 * @param password
	 * @return
	 * @throws CipherException
	 * @throws IncorrectPasswordException
	 * @throws IncorrectDocumentFormatException
	 */
	public byte[] getDecryptedBytes(byte[] ciphertext, char[] password) throws CipherException, IncorrectPasswordException, IncorrectDocumentFormatException {		
		InputStream is = new ByteArrayInputStream(ciphertext);
		
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			BufferedInputStream bis = new BufferedInputStream(is);

			byte[] data = new byte[1024];
			int bytesRead;
			while((bytesRead = bis.read(data)) > -1){
				baos.write(data, 0, bytesRead);
			}

			baos.flush();
			baos.close();
			
			return baos.toByteArray();
		}
		catch (IOException ioe){
			throw new CipherException(ioe);
		}
	}
	
	/**
	 * Returns an encrypted version of the given plaintext string, using the given password.
	 * @param plaintext
	 * @param password
	 * @return
	 * @throws CipherException
	 */
	public String getEncryptedString(String plaintext, char[] password) throws CipherException {
		try {
			return byteToHexString(getEncryptedBytes(plaintext.getBytes("UTF8"), password));
		}
		catch (UnsupportedEncodingException uee){
			throw new CipherException(uee);
		}
	}

	/**
	 * Returns a decrypted version of the given ciphertext string, using the given password.
	 * @param ciphertext
	 * @param password
	 * @return
	 * @throws CipherException
	 * @throws IncorrectPasswordException
	 * @throws IncorrectDocumentFormatException
	 */
	public String getDecryptedString(String ciphertext, char[] password) throws CipherException, IncorrectPasswordException, IncorrectDocumentFormatException {
		try {
			return new String(getDecryptedBytes(hexStringToByte(ciphertext), password), "UTF8");
		}
		catch (UnsupportedEncodingException uee){
			throw new CipherException(uee);
		}
	}

	/**
	 * Returns a byte array for use as a key, given a password and salt.
	 * Uses a MessageDigest to hash the input parameters
	 * @param password
	 * @param keyLength
	 * @return
	 */
	private byte[] getKeyFromPassword(char[] password, byte[] salt, int keyLength) throws Exception {
		MessageDigest md = MessageDigest.getInstance(digestAlgorithm);

		//The primitiveKey is an array which consists of the byte values of password,
		// concatenated with the salt.  This is hashed (and potentially cut off)
		// to provide the encryption key for the cipher.
		byte[] primitiveKey = new byte[password.length * 2 + salt.length];
		for (int i = 0; i < password.length; i++){
			primitiveKey[i * 2] = (byte) (password[i] >> 8);
			primitiveKey[i * 2 + 1] = (byte) (password[i] & 0xFF);
		}
		System.arraycopy(salt, 0, primitiveKey, password.length * 2, salt.length);
		md.update(primitiveKey);

		//The key may need to be less than the length of the digest. 
		byte[] key = new byte[keyLength];
		System.arraycopy(md.digest(), 0, key, 0, key.length);

		return key;
	}

	/**
	 * Checks if the canary is decrypted.  Just checks each byte from
	 * the input stream with the known good value, supplied by the 
	 * implementing class' getCanary() method.
	 * @param is
	 * @return
	 * @throws IOException
	 */
	private boolean isCanaryDecrypted(InputStream is) throws IOException {
		byte[] canary = getCanary();

		for (int i = 0; i < canary.length; i++){
			byte temp = (byte) is.read();
//			System.out.println(Integer.toHexString(temp) + " == " + Integer.toHexString(canary[i]));
			if (temp != canary[i])
				return false;
		}
		return true;
	}

	/**
	 * Checks if the header is correct.  Just checks each byte from
	 * the input stream with the known good value, supplied by the 
	 * implementing class' getHeader() method.
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public boolean isHeaderCorrect(InputStream is) throws IOException {
		byte[] header = getHeader();

		for (int i = 0; i < header.length; i++){
			byte temp = (byte) is.read();
			if (temp != header[i])
				return false;
		}
		return true;
	}

	/**
	 * The byte array to use as the header.  This should be a constant and
	 * distinct byte array for each file type.  It is stored in plain text
	 * at the beginning of the file, and is used by the getCipher*Stream()
	 * methods to verify that the file is of the correct type.  It can also 
	 * be used by the OS to associate files with programs, if the windowing
	 * system supports signature based file association.
	 * 
	 * You should not change this between versions of the data file, or else
	 * you will not be able to load old files in the new program version.
	 * @return An array of bytes representing the document's header (or file signature).
	 */
	protected abstract byte[] getHeader();

	/**
	 * The byte array to use as the canary.  This is a short (about 8 - 16
	 * byte) string which is placed at the beginning of the file, right after
	 * the salt.  The canary is encrypted, and is checked by the getCipher*Stream()
	 * methods to ensure that the file is correctly decoded (i.e., that
	 * the password is correct).
	 * 
	 * By default, this is set to the byte equivalent of '01234456789abcdef'.  This
	 * method is provided for you to override this if you wish, but there is 
	 * probably no reason to do so.
	 * 
	 * You should definitely not change this between versions of the data file, or else
	 * you will not be able to load old files in the new program version.
	 * @return An array of bytes representing the document's canary value
	 */
	protected byte[] getCanary() {
		return "0123456789abcdef".getBytes();
	}

	/**
	 * Should we include the date timestamp at the beginning of the file?  This value is
	 * unencrypted, and will appear right after the header.
	 * 
	 * You cannot change this value for different versions of the program, or the data files
	 * will be invalid.
	 * 
	 * @return true if we should save the date, false otherwise. 
	 */
	public abstract boolean isSaveDate();
	
	/**
	 * Should we filter the data through gzip before encrypting it?  For most situations, 
	 * this should be true.  If you need to use this for debugging purposes, or if the 
	 * data is already very compressed, or if for speed reasons, it is better to not
	 * compress the data, you can return false here.
	 * 
	 * When reading the files, we first check if it is compressed; if so, we read it in, 
	 * but if not we assume the data is not compressed, and try it again.  Because of
	 * this, you can mix and match compression in files, with no ill effects.
	 * @return
	 */
	public abstract boolean isCompressData();

	/**
	 * Returns a byte array representation of a long value.  The returned
	 * byte array will be exactly 8 bytes (64 bits) long.
	 * @param l
	 * @return
	 */
	public static byte[] longToByte(long l){
		byte[] b = new byte[8];
		long mask = 0xFF; 
		for (int i = 0; i < 8; i++) {
			b[i] = (byte) ((mask << (i * 8) & l) >>> i * 8);
		}
		
		return b;
	}
	
	/**
	 * Returns a long from a byte array representation.  The given byte array
	 * must be exactly 8 bytes (64 bits) in length; if it more or less, we 
	 * return 0.
	 * 
	 * @param b
	 * @return
	 */
	public static long byteToLong(byte[] b){
		if (b.length != 8)
			return 0; //Invalid byte array
		long l = 0;
		long mask = 0xFF; 
		for (int i = 0; i < 8; i++) {
			l = l | (mask << (i * 8)) & ((long) b[i]) << i * 8;
		}
		
		return l;
	}
	
	/**
	 * Converts the given byte array to a string representation of the 
	 * byte array, encoded as hex digits.
	 * @param bytes
	 * @return
	 */
	public static String byteToHexString(byte[] bytes){
		StringBuilder sb = new StringBuilder();
		
		for (byte b : bytes) {
			int i = (int) b & 0xFF;
			if (i <= 0xF)
				sb.append("0");
			sb.append(Integer.toHexString(i));
		}
		
		return sb.toString();
	}
	
	/**
	 * Converts the given string representation of the hex byte array
	 * as a byte array.
	 * @param string
	 * @return
	 */
	public static byte[] hexStringToByte(String string){
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		for(int i = 0; i < string.length();){
			int b = Integer.parseInt(string.substring(i, i + 2), 16);
			baos.write(b);
			i += 2;
		}
		
		return baos.toByteArray();
	}
}
