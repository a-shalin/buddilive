package ca.digitalcave.moss.restlet.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class EscapeUtil {

	private static final Pattern whitelistRegex = Pattern.compile("&lt;(br/?>|/?pre/?>|/?img/?>|/?b>|/?i>)", Pattern.CASE_INSENSITIVE);
	public static String sanitizeString(String value){
		return sanitizeString(value, whitelistRegex);
	}
	public static String sanitizeString(String value, Pattern whitelistedHtml){
		if (value == null){
			return null;
		}
		value = value.replaceAll("<", "&lt;");	//We escape all HTML tags first...
		if (whitelistedHtml != null){
			final Matcher m = whitelistedHtml.matcher(value);
			final StringBuffer sb = new StringBuffer();
			while (m.find()) {
				m.appendReplacement(sb, "<" + m.group(1)); //... and whitelist certain ones
			}
			m.appendTail(sb);
			return sb.toString();
		}
		else {
			return value;
		}
	};
	
	/**
	 * Returns a string with some of the sanitization reversed.  This should only be used in places where it is safe.
	 */
	public static String reverseSanitizedString(String value){
		if (value == null){
			return null;
		}
		
		value = value.replaceAll("&lt;", "<");
		return value;
	}

	/**
	 * This is a copy of Apache Commons Lang 2 escapeSql function.  It is very basic and should probably be replaced with something 
	 * better...
	 * @param str
	 * @return
	 */
	public static String escapeSql(String str) {
		if (str == null) {
			return null;
		}
		return StringUtils.replace(str, "'", "''");
	}
}
