package ca.digitalcave.moss.restlet.resource.ui;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.restlet.data.ChallengeResponse;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.freemarker.TemplateRepresentation;
import org.restlet.representation.OutputRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.digitalcave.moss.restlet.CookieAuthenticator;
import ca.digitalcave.moss.restlet.model.AuthUser;
import ca.digitalcave.moss.restlet.plugin.AuthenticationHelper;
import ca.digitalcave.moss.restlet.resource.LoginResource;
import ca.digitalcave.moss.restlet.util.LocalizationUtil;
import ca.digitalcave.moss.restlet.util.OverridableResourceBundle;
import freemarker.cache.ClassTemplateLoader;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Version;

public class UIFreemarkerResource extends ServerResource {
	protected static Configuration freemarkerConfig = null;

	protected void doInit() throws ResourceException {
		LocalizationUtil.addVariants(getVariants(), MediaType.APPLICATION_JAVASCRIPT);
		LocalizationUtil.addVariants(getVariants(), MediaType.IMAGE_ALL);
	}
	
	public Representation get(Variant variant) throws ResourceException {
		final String path = "extjs/" + (getReference().getRemainingPart().replaceAll("\\?.*$", "") + "." + getOriginalRef().getExtensions()).replaceAll("^/", "");
		
		
		if (variant.getMediaType().equals(MediaType.APPLICATION_JAVASCRIPT)){
			final AuthenticationHelper helper = CookieAuthenticator.getAuthenticationHelper(getRequest());
			
			final Map<String, Object> dataModel = helper.getConfig().getMap();
			dataModel.put("routerAttachPoint", getReference().toString().replace(getRootRef().toString(), "").replace(getReference().getRemainingPart(), "").replaceFirst("^/", ""));
			
			final Locale locale = LocalizationUtil.getLocale(variant);
			dataModel.put("i18n", new OverridableResourceBundle(
					(helper.getConfig().i18nBaseCustom == null ? null : ResourceBundle.getBundle(helper.getConfig().i18nBaseCustom, locale)),
					ResourceBundle.getBundle("ca.digitalcave.moss.restlet.i18n", locale)	//Built in i18n
				));
			
			final AuthUser user = (AuthUser) getRequest().getClientInfo().getUser();
			final ChallengeResponse cr = getChallengeResponse();
			
			final String nextStep = LoginResource.getNextStep(cr, user);
			if (nextStep != null){
				dataModel.put("activeItem", nextStep);
			}
			else {
				dataModel.put("activeItem", "authenticate");
			}
			
			final TemplateRepresentation entity = new TemplateRepresentation(path, getFreemarkerConfig(), dataModel, variant.getMediaType());
			
			if (entity.getTemplate() == null) throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND);
			entity.setModificationDate(new Date());
			return entity;
		}
		else {
			final InputStream is = this.getClass().getResourceAsStream(path);
			if (is == null) throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND);
			return new OutputRepresentation(variant.getMediaType()) {
				@Override
				public void write(OutputStream os) throws IOException {
					copyStream(is, os);
				}
			};
		}
	}

	protected Configuration getFreemarkerConfig(){
		if (freemarkerConfig == null){
			Configuration config = new Configuration(new Version(2, 3, 28));
			config.setTemplateLoader(new ClassTemplateLoader(UIFreemarkerResource.class, ""));
			config.setDefaultEncoding("UTF-8");
			config.setLocalizedLookup(false);
			config.setLocale(Locale.ENGLISH);
			config.setTemplateUpdateDelayMilliseconds(1000);
			final BeansWrapper beansWrapper = new BeansWrapper(new Version(2, 3, 28));
			beansWrapper.setSimpleMapWrapper(true);
			config.setObjectWrapper(beansWrapper);
			config.setDateFormat("yyyy'-'MM'-'dd");
			config.setDateTimeFormat("yyyy'-'MM'-'dd' 'HH:mm");
			config.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
			freemarkerConfig = config;
		}
		return freemarkerConfig;
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
