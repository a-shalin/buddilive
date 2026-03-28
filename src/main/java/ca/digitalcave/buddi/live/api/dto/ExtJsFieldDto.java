package ca.digitalcave.buddi.live.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExtJsFieldDto(String xtype,
						 String messageBody,
						 String type,
						 String name,
						 String fieldLabel,
						 String value,
						 String boxLabel,
						 String labelSeparator,
						 String html) {

	public static ExtJsFieldDto selfDocumentingField(final String messageBody, final String type, final String name, final String fieldLabel, final String value) {
		return new ExtJsFieldDto("selfdocumentingfield", messageBody, type, name, fieldLabel, value, null, null, null);
	}

	public static ExtJsFieldDto selfDocumentingCheckbox(final String messageBody, final String boxLabel, final String name, final String fieldLabel, final String labelSeparator) {
		return new ExtJsFieldDto("selfdocumentingfield", messageBody, "checkbox", name, fieldLabel, null, boxLabel, labelSeparator, null);
	}

	public static ExtJsFieldDto label(final String html) {
		return new ExtJsFieldDto("label", null, null, null, null, null, null, null, html);
	}
}
