package ca.digitalcave.buddi.live.db.util;

public class DatabaseException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	public DatabaseException(final String message) {
		super(message);
	}
	public DatabaseException(final Throwable e) {
		super(e);
	}
	public DatabaseException(final String message, final Throwable e) {
		super(message, e);
	}
}
