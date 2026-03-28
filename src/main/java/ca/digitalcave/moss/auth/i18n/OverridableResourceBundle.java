package ca.digitalcave.moss.auth.i18n;

import java.util.*;

public class OverridableResourceBundle extends ResourceBundle {
	private final ResourceBundle bundle;
	private final ResourceBundle defaultBundle;
	
	public OverridableResourceBundle(final ResourceBundle bundle, final ResourceBundle defaultBundle) {
		this.bundle = bundle;
		this.defaultBundle = defaultBundle;
	}
	
	@Override
	public Enumeration<String> getKeys() {
		final Set<String> keys = new HashSet<String>();
		
		if (bundle != null){
			Enumeration<String> e = bundle.getKeys();
			while (e.hasMoreElements()){
				keys.add(e.nextElement());
			}
		}
		if (defaultBundle != null){
			Enumeration<String> e = defaultBundle.getKeys();
			while (e.hasMoreElements()){
				keys.add(e.nextElement());
			}
		}
		
		return Collections.enumeration(keys);
	}

	@Override
	protected Object handleGetObject(final String key) {
		if (bundle != null){
			if (bundle.containsKey(key)){
				try {
					return bundle.getObject(key);
				}
				catch (Exception e){
					;
				}
			}
		}
		if (defaultBundle != null){
			if (defaultBundle.getObject(key) != null){
				return defaultBundle.getObject(key);
			}
		}
		return null;
	}
	
}
