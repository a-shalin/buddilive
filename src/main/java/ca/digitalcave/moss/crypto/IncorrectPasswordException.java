/*
 * Created on Aug 20, 2007 by wyatt
 */
package ca.digitalcave.moss.crypto;

public class IncorrectPasswordException extends Exception {
	public static final long serialVersionUID = 0;

	public IncorrectPasswordException() {
		super();
	}
	
	public IncorrectPasswordException(String message) {
		super(message);
	}
	
	public IncorrectPasswordException(String message, Exception e) {
		super(message, e);
	}
	
	public IncorrectPasswordException(Exception e) {
		super(e);
	}
}
