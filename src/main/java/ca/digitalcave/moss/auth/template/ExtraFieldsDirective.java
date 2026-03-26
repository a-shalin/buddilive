package ca.digitalcave.moss.auth.template;

import java.io.Writer;
import java.util.ResourceBundle;

public abstract class ExtraFieldsDirective {
	public abstract void writeFields(Writer out, ResourceBundle i18n);
}
