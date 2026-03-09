/*
 * Created on Dec 28, 2006 by wyatt
 */
package ca.digitalcave.moss.common;

import java.io.File;

import javax.swing.UIManager;

public class OperatingSystemUtil {
	private static Boolean isMac;
	private static Boolean isWindows;

	/**
	 * Returns true iff the program is being run on a Mac, and one of either 
	 * Aqua or Quaqua look and feels are being used.
	 * @return
	 */
	public static boolean isMacAqua(){
		return (isMac() 
				&& (UIManager.getLookAndFeel().getClass().getName().equals("ch.randelshofer.quaqua.QuaquaLookAndFeel"))
				|| UIManager.getLookAndFeel().getClass().getName().equals("apple.laf.AquaLookAndFeel"));
	}
	
	/**
	 * Is the program being run on a Macintosh?
	 * @return <code>true</code> if program is being run on a Mac, <code>false</code> otherwise. 
	 */
	public static boolean isMac(){
		if (isMac == null){
			isMac = System.getProperty("os.name").startsWith("Mac OS");
		}

		return isMac;
	}

	/**
	 * Is the program being run on Windows?
	 * @return <code>true</code> if program is being run on Windows, <code>false</code> otherwise. 
	 */
	public static Boolean isWindows(){
		if (isWindows == null){
			isWindows = System.getProperty("os.name").startsWith("Windows");
		}

		return isWindows;
	}
	
	/**
	 * Returns the file with the given name, in the correct user folder
	 * for the current OS and given program name.  Does no checking if this
	 * is valid, existing, writable, etc. 
	 * @param programName
	 * @param fileName
	 * @return
	 */
	public static File getUserFile(String programName, String fileName){
		return new File(getUserFolder(programName) + File.separator + fileName);
	}
	
	/**
	 * Returns the location of the standard location for programs.
	 * 
	 * This is C:\Program Files\Program Name on Windows,  
	 * /Applications on the Mac, and /usr/bin on Unix.
	 * @param programName
	 * @return
	 */
	public static File getProgramsFolder(String programName){
		String location;
		if (isMac()){
			location =  
				"/Applications";
		}
		else if (isWindows()){
			final String programFiles = "PROGRAMFILES";
			if (System.getenv(programFiles) != null && System.getenv(programFiles).length() > 0)
				location = 
					System.getenv(programFiles)
					+ "\\"
					+ programName;
			else
				location = "C:\\Program Files\\"
					+ programName; 
		}
		else{ //Probably Linux / Unix...
			location = "/usr/bin";
		}
		
		return new File(location);
	}

	/**
	 * Returns the user folder for the given operating system.  For Windows, this is 
	 * Documents and Settings\\user\\Application Data\\Program Name\\.
	 * For Mac, this is ~/Library/Application Support/Program Name/.
	 * For others (we assume Unix), this is ~/.program_name/. 
	 * @param programName
	 * @param fileName
	 * @return
	 */
	public static File getUserFolder(String programName){
		String location;
		if (isMac()){
			location = 
				System.getProperty("user.home") 
				+ "/Library/Application Support/"
				+ programName;
		}
		else if (isWindows()){
			final String appData = "APPDATA";
			if (System.getenv(appData) != null && System.getenv(appData).length() > 0)
				location = 
					System.getenv(appData)
					+ "\\"
					+ programName;
			else
				location = 
					System.getProperty("user.home")
					+ "\\Application Data\\"
					+ programName; 
		}
		else{ //Probably Linux / Unix...
			location = 
				System.getProperty("user.home") 
				+ File.separator 
				+ "." + programName.replaceAll(" ", "_").toLowerCase();
		}
		
		return new File(location);
	}
	
	public static File getHomeDirectory(){
		return new File(System.getProperty("user.home"));
	}
	
	/**
	 * Return the Pictures folder for the OS.  On Windows, it is My Pictures; 
	 * on Macintosh, it's Pictures, on anything else, it's just the home directory.
	 * 
	 * This does not take into account different locales for the OS.
	 * @return
	 */
	public static File getPicturesFolder(){
		if (isWindows()){
			return new File(System.getProperty("user.home") + "\\My Pictures");
		}
		else if (isMac()){
			return new File(System.getProperty("user.home") + "/Pictures");
		}
		else {
			return new File(System.getProperty("user.home"));
		}
	}
	
	
	/**
	 * Returns the user's log folder on the current OS.  As this may be related
	 * to the program name, we include that as an argument.
	 * 
	 * For Windows, this is C:\\Documents and Settings\\user\\Application Data\\programName\\fileName.
	 * For Mac, this is ~/Library/Application Support/programName/fileName.
	 * For others, this is ~/.program_name/fileName. 
	 * @param programName
	 * @param fileName
	 * @return
	 */
	public static File getLogFile(String programName, String fileName){
		String location;
		if (isMac()){
			location = 
				System.getProperty("user.home") 
				+ "/Library/Logs/"
				+ fileName; 
		}
		else {
			location = getUserFile(programName, fileName).getAbsolutePath();
		}
		
		return new File(location);
	}
}
