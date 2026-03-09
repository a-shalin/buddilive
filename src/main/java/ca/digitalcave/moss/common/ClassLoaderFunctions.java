/*
 * Created on Oct 6, 2006 by wyatt
 */
package ca.digitalcave.moss.common;

import java.awt.Image;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ImageIcon;

/**
 * Utility to load resources and objects from a jar file.  Basically a 
 * wrapper to URLClassLoader, which formats a URL from a given Jar file.
 * Also includes utilities to read entries from a Jar file.
 *  
 * @author wyatt
 *
 */
public class ClassLoaderFunctions extends URLClassLoader {

	/**
	 * Creates a new object specified by className from the given Jar file.
	 * Will not return a null object - if object is null, it will throw
	 * an exception.
	 * @param jarFile Jar file to load from
	 * @param className Class name to load
	 * @return The newly created object
	 * @throws JarLoaderException
	 */
	public static Object getObject(File jarFile, String className) throws JarLoaderException {
		try {
			String jarFileURL = "jar:file:" + jarFile.getAbsolutePath() + "!/";
//			Log.debug("Attempting to load from " + jarFileURL);
			URL url = new URL(jarFileURL);

			ClassLoaderFunctions jl = new ClassLoaderFunctions(url);
			return jl.getObject(className);
		}
		catch(MalformedURLException mue){
			throw new JarLoaderException(mue);
		}
	}

	/**
	 * Returns a collection of all the entries in a jar file.  Can be
	 * used to browse for a particular class, etc.
	 * @param jarFile Jarfile to browse
	 * @return
	 */
	public static Collection<JarEntry> getAllClasses(File jarFile, String rootClass){
		Collection<JarEntry> jarEntries = new LinkedList<JarEntry>();

		try{
			JarFile jar = new JarFile(jarFile);
			for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();){
				JarEntry je = e.nextElement();
				if (!je.isDirectory()
						&& je.getName().endsWith(".class")
						&& (rootClass == null || je.getName().startsWith(rootClass))){
					jarEntries.add(je);
//					Log.debug("JarLoader.getAllClasses(): added " + je.getName() + " to collection of jar entries.");
				}
			}
		}
		catch (IOException ioe){
			Logger.getLogger(ClassLoaderFunctions.class.getName()).log(Level.WARNING, "Error loading resource from jar", ioe);
		}

		return jarEntries;
	}

	/**
	 * Returns the given resource as a stream from the specified jar file.  Useful for
	 * loading plugins and other objects from different files. 
	 * @param jarFile
	 * @param resource
	 * @return
	 */
	public static InputStream getResourceAsStreamFromJar(File jarFile, String resource) {
		try{
			String jarFileURL = "jar:file:" + jarFile.getAbsolutePath() + "!/";
//			Log.info("Attempting to load " + resource + " from " + jarFileURL);
			URL url = new URL(jarFileURL);

			ClassLoaderFunctions jl = new ClassLoaderFunctions(url);
			return jl.getResourceAsStream(resource);
		}
		catch (MalformedURLException mfue){
			return null;
		}
	}

	/**
	 * Returns the given resource as a stream.
	 * @param resource
	 * @return
	 */
	public static InputStream getResourceAsStreamFromClasspath(String resource) {
		return new Object().getClass().getResourceAsStream(resource);
	}

	/**
	 * Gets a URL representing the given resource on the class path.  Use relative 
	 * paths, from the root of the jar file.  For instance, if you want to load 
	 * the file example.png, in the folder Resources, use the string "Resources/example.png",
	 * but not "/Resources/example.png".
	 * @param resource
	 * @return
	 */
	public static URL getResourceAsUrl(String resource){
		if (resource != null){
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			if (cl == null) {
				cl = ClassLoaderFunctions.class.getClassLoader();
			}

			URL urlResource = cl.getResource(resource);
			if (urlResource != null) {
				return urlResource;
			}
		}
		return null;
	}

	/**
	 * Creates a new object from a given class name.  Will not return 
	 * a null object - if the object is null, it will throw an exception
	 * @param className
	 * @return
	 * @throws JarLoaderException
	 */
	private Object getObject(String className) throws JarLoaderException {
		try{
			Logger.getLogger(this.getClass().getName()).finest("Trying to load object from class " + className);
			Class<?> c = this.loadClass(className);
			Object o = c.newInstance();

			if (o == null)
				throw new JarLoaderException("Loaded object is null.");

			return o;
		}
		catch (IllegalAccessException iae){
			Logger.getLogger(this.getClass().getName()).finest("Could not instantiate object: " + iae.getMessage());
		}
		catch (InstantiationException ie){
			Logger.getLogger(this.getClass().getName()).finest("Could not instantiate object: " + ie.getMessage());
		}
		catch (NoClassDefFoundError ncdfe){
			throw new JarLoaderException(ncdfe);
		}
		catch (Exception e){
			throw new JarLoaderException(e);
		}

		return null;
	}

	public static Class<?> getClass(File jarFile, String className) throws JarLoaderException {
		try {
			String jarFileURL = "jar:file:" + jarFile.getAbsolutePath() + "!/";
			Logger.getLogger(ClassLoaderFunctions.class.getName()).finest("Attempting to load from " + jarFileURL);
			URL url = new URL(jarFileURL);

			ClassLoaderFunctions jl = new ClassLoaderFunctions(url);
			return Class.forName(className, true, jl);
		}
		catch(ClassNotFoundException ex) {
			throw new JarLoaderException(ex);
		}
		catch(MalformedURLException mue){
			throw new JarLoaderException(mue);
		}
	}

	/**
	 * Returns an ImageIcon from an image loaded in the current classpath.  We
	 * first check for files in the current path, then we look in jar
	 * files.
	 * 
	 * @param name The relative path and name of the file.  Don't include the leading slash.
	 * @return An ImageIcon with the given icon, if available, or an empty one otherwise.
	 */
	public static Image getImageFromClasspath(String name){
		ImageIcon icon;

		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (null == cl) {
			cl = ClassLoaderFunctions.class.getClassLoader();
		}

		URL imageResource = null;
		File f = new File(name);
		if (f.exists() && !f.isDirectory() && f.canRead()){
			try {
				imageResource = f.toURL();
			}
			catch (MalformedURLException mue){}
		}

		if (imageResource == null)
			imageResource = cl.getResource(name);

		if (imageResource != null) {
			icon = new ImageIcon(Toolkit.getDefaultToolkit().getImage(imageResource));
		}
		else {
			icon = new ImageIcon();
		}

		return icon.getImage();
	}

	private ClassLoaderFunctions(URL url){
		super(new URL[] { url });
	}

	/**
	 * @author wyatt
	 *
	 * An exception thrown when loading jars.
	 */
	public static class JarLoaderException extends Exception {
		public static final long serialVersionUID = 0;

		public JarLoaderException(Exception e){
			super(e);
		}

		public JarLoaderException(String s){
			super(s);
		}

		public JarLoaderException(Error e){
			super(e);
		}
	}
}
