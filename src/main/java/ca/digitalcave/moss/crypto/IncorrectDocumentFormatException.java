/*
 * Created on Aug 20, 2007 by wyatt
 */
package ca.digitalcave.moss.crypto;

public class IncorrectDocumentFormatException extends Exception {
	public static final long serialVersionUID = 0;

	public IncorrectDocumentFormatException() {
		super();
	}
	
	public IncorrectDocumentFormatException(String message) {
		super(message);
	}
	
	public IncorrectDocumentFormatException(String message, Exception e) {
		super(message, e);
	}
	
	public IncorrectDocumentFormatException(Exception e) {
		super(e);
	}
}
