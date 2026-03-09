package ca.digitalcave.moss.common;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamUtil {
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
}
