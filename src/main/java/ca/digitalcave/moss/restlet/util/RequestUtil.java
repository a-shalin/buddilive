package ca.digitalcave.moss.restlet.util;

import org.restlet.Request;
import org.restlet.util.NamedValue;

public class RequestUtil {

	/**
	 * Returns the original request URL, relying on X-Forwarded-* headers to reconstruct as best as possible.
	 */
	public static String getRequestURL(Request request) {
		String protocol = request.getRootRef().getScheme();
		String host = request.getRootRef().getHostDomain();
		String port = request.getRootRef().getHostPort() + "";
		String path = request.getOriginalRef().getPath();
		
		for (NamedValue<String> namedValue : request.getHeaders()) {
			if ("X-Forwarded-For".equalsIgnoreCase(namedValue.getName())){
				namedValue.getValue();
			}
			else if ("X-Forwarded-Proto".equalsIgnoreCase(namedValue.getName())){
				protocol = namedValue.getValue();
			}
			else if ("X-Forwarded-Host".equalsIgnoreCase(namedValue.getName()) || "X-Forwarded-Server".equalsIgnoreCase(namedValue.getName())){
				host = namedValue.getValue();
			}
			else if ("X-Forwarded-Port".equalsIgnoreCase(namedValue.getName())){
				port = namedValue.getValue();
			}
		}
		if (("http".equals(protocol) && "80".equals(port)) || ("https".equals(protocol) && "443".equals(port))){
			port = "";
		}
		else if ("-1".equals(port)){
			//This means that we don't have X-Forwarded-Proto and X-Forwarded-Port.  We default to assuming https on 443.
			port = "";
			protocol = "https";
		}
		else {
			port = ":" + port;
		}
		return new StringBuilder().append(protocol).append("://").append(host).append(port).append(path).toString();
	}
	
	/**
	 * Returns the path of the request underneath the root reference.
	 */
	public static String getPath(Request request){
		final String rootRef = request.getRootRef().getPath();
		final String originalRef = request.getOriginalRef().getPath();
		final String path = originalRef.replace(rootRef, "").replace("?" + request.getOriginalRef().getQuery(), "").replaceAll("^/", "");

		return path;
	}
	
	/**
	 * Returns the original request URL, relying on X-Forwarded-* headers, up to the root path (including trailing slash)
	 */
	public static String getOriginalRoot(Request request){
		final String requestUrl = getRequestURL(request);
		final String path = getPath(request);
		return requestUrl.replace(path, "");
	}
}
