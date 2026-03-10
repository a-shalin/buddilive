package ca.digitalcave.moss.restlet.resource.ui;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.OutputRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.digitalcave.moss.restlet.util.LocalizationUtil;

public class UIFreemarkerResource extends ServerResource {

	protected void doInit() throws ResourceException {
		LocalizationUtil.addVariants(getVariants(), MediaType.APPLICATION_JAVASCRIPT);
		LocalizationUtil.addVariants(getVariants(), MediaType.IMAGE_ALL);
	}

	public Representation get(Variant variant) throws ResourceException {
		final String path = "extjs/" + (getReference().getRemainingPart().replaceAll("\\?.*$", "") + "." + getOriginalRef().getExtensions()).replaceAll("^/", "");

		final InputStream is = this.getClass().getResourceAsStream(path);
		if (is == null) throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND);
		return new OutputRepresentation(variant.getMediaType()) {
			@Override
			public void write(OutputStream os) throws IOException {
				copyStream(is, os);
			}
		};
	}

	private static void copyStream(InputStream is, OutputStream os) throws IOException {
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
