package ca.digitalcave.buddi.live.controller;

public enum Action {
	INSERT("insert"),
	UPDATE("update"),
	DELETE("delete"),
	UNDELETE("undelete"),
	COPY_FROM_PREVIOUS("copyFromPrevious"),
	SET("set"),
	INVALIDATE_TOTP_BACKUPS("invalidatetotpbackups");

	private final String value;

	Action(final String value) {
		this.value = value;
	}

	public static Action fromString(final String value) {
		for (final Action action : values()) {
			if (action.value.equals(value)) {
				return action;
			}
		}

		return null;
	}
}
