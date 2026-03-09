package ca.digitalcave.moss.restlet.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import org.restlet.data.Status;
import org.restlet.resource.ResourceException;

public class StringUtil {
	public static String verifyLength(String value, String name, int maxLength) throws ResourceException {
		if (value != null && value.length() > maxLength){
			throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, name + " is too long.");
		}
		return value;
	}
	
	public static String getHtmlFormattedStackTrace(Throwable e){
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final PrintWriter pw = new PrintWriter(baos);
		e.printStackTrace(pw);
		pw.close();
		return baos.toString().replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;").replaceAll("\n", "<br/>");
	}
}
