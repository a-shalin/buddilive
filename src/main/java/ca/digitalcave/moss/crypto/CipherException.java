/*
 * Created on Aug 20, 2007 by wyatt
 */
package ca.digitalcave.moss.crypto;

public class CipherException extends Exception {
	public static final long serialVersionUID = 0;

	public CipherException() {
		super();
	}
	
	public CipherException(String message) {
		super(message);
	}
	
	public CipherException(String message, Exception e) {
		super(message, e);
	}
	
	public CipherException(Exception e) {
		super(e);
	}
}
