package ca.digitalcave.moss.restlet.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamUtil {
	/**
	 * Reads the supplied input stream into a UTF-8 encoded String.  Probably best to not use this for large responses.
	 */
	public static String readStream(InputStream is) throws IOException {
		BufferedInputStream bis = new BufferedInputStream(is);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		byte[] data = new byte[1024];
		int bytesRead;
		while((bytesRead = bis.read(data)) > -1){
			baos.write(data, 0, bytesRead);
		}

		baos.flush();
		baos.close();
		
		bis.close();
		is.close();
		
		return baos.toString("UTF-8");
	}
	
	/**
	 * Copy the contents of the given input stream to the given output stream.
	 * @param is
	 * @param os
	 * @throws IOException
	 */
	public static void writeStream(String data, OutputStream os) throws IOException {
		ByteArrayInputStream bis = new ByteArrayInputStream(data.getBytes("UTF-8"));
		BufferedOutputStream bos = new BufferedOutputStream(os);

		byte[] buffer = new byte[1024];
		int bytesRead;
		while((bytesRead = bis.read(buffer)) > -1){
			bos.write(buffer, 0, bytesRead);
		}

		bos.flush();
		bos.close();
	}
	
	/**
	 * Copy the contents of the given input stream to the given output stream.
	 * @param is
	 * @param os
	 * @throws IOException
	 */
	public static void copyStream(InputStream is, OutputStream os) throws IOException {
		BufferedInputStream bis = new BufferedInputStream(is);
		BufferedOutputStream bos = new BufferedOutputStream(os);

		byte[] data = new byte[1024];
		int bytesRead;
		while((bytesRead = bis.read(data)) > -1){
			bos.write(data, 0, bytesRead);
		}

		bos.flush();
		bos.close();
	}
	
	/**
	 * Reads an input stream to a bytes array
	 */
	public static byte[] getBytes(InputStream is) throws IOException {
		if (is == null){
			return null;
		}
		
		BufferedInputStream bis = new BufferedInputStream(is);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		byte[] buffer = new byte[1024];
		int bytesRead;
		while((bytesRead = bis.read(buffer)) > -1){
			baos.write(buffer, 0, bytesRead);
		}

		baos.flush();
		baos.close();
		
		return baos.toByteArray();
	}
}
