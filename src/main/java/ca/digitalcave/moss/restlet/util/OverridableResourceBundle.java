package ca.digitalcave.moss.restlet.util;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

public class OverridableResourceBundle extends ResourceBundle {
	private final ResourceBundle bundle;
	private final ResourceBundle defaultBundle;
	
	public OverridableResourceBundle(ResourceBundle bundle, ResourceBundle defaultBundle) {
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
	protected Object handleGetObject(String key) {
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
