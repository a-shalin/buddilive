/*
 * Created on Apr 23, 2007 by wyatt
 */
package ca.digitalcave.moss.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * @author wyatt
 * A simple class which allows you to define the current version.
 * This implements Comparable for comparison between versions.
 */
public class Version implements Comparable<Version>{
	private final Integer major;
	private final Integer minor;
	private final Integer release;
	private final Integer patch;
	
	/**
	 * Returns a Version object obtained from a file resource.  This file can
	 * either be on the file system itself, or within the main .jar file.
	 * 
	 * This is a convenience method to call the InputStream constructor
	 * of Version twice; first we try to load from the .jar file by prepending
	 * a / to the resourceNam, and if that does not work, we try to load from
	 * a FileInputStream, relative to the current working directory.
	 * 
	 * This resource should be just the name and (optionally) leading 
	 * relative path.  For instance, if you were loading this file from the
	 * root of your project, and the file is called 'version.txt', just
	 * pass the string 'version.txt'.
	 * @param resourceName
	 * @return
	 */
	public static Version getVersionResource(String resourceName){
		Version version = new Version(Version.class.getResourceAsStream("/" + resourceName));
		if (version.toString().equals("0.0.0.0")){
			try {
				version = new Version(new FileInputStream(new File(resourceName)));
			}
			catch (IOException ioe){
				version = new Version("0.0.0.0");
			}
		}
		
		return version;
	}
	
	/**
	 * Creates a new Version object, parsing the given input stream
	 * for the first occurence of a version pattern.  We define a 
	 * version pattern as something which matches the regex
	 * "\d+\.\d+\.\d+\.\d+" (not including quotes).
	 * 
	 * If there is an I/O error, or the string is not found,
	 * we return 0.0.0.0 as the version.
	 * @param is
	 */
	public Version(InputStream is) {
		this(findVersion(is));
	}
	
	private static String findVersion(InputStream is){
		if (is != null){
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));

			String temp;
			try {
				while ((temp = reader.readLine()) != null){
					Pattern p = Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+");
					Matcher m = p.matcher(temp);
					m.find();
					try {
						return m.group();
					}
					catch (IllegalStateException ise){}
				}
			}
			catch (IOException ioe){}
		}
		
		return "0.0.0.0";
	}
	
	/**
	 * Returns a new Version object using the specified string as a version number.
	 * The string should be in the form "X.Y.R.P", where X is the Major version,
	 * Y is the minor version, R is the release number, and P is the
	 * Patch number - for instance, "2.6.12.0".
	 * 
	 * You can also include fewer numbers in the string - this will result in the 
	 * less signifigant digits being set to zero.  For instance, the string
	 * "2.6" is functionally equivalent to "2.6.0.0".
	 * @param version
	 */
	public Version(String version) {
		String[] splitVersion = version.split("\\.");
		
		if (splitVersion.length > 0) major = Integer.parseInt(splitVersion[0]);
		else major = 0;
		
		if (splitVersion.length > 1) minor = Integer.parseInt(splitVersion[1]);
		else minor = 0;
		
		if (splitVersion.length > 2) release = Integer.parseInt(splitVersion[2]);
		else release = 0;
		
		if (splitVersion.length > 3) patch = Integer.parseInt(splitVersion[3]);
		else patch = 0;
	}

	/**
	 * Specifies the version number using four integers.
	 * @param major
	 * @param minor
	 * @param release
	 * @param patch
	 */
	public Version(int major, int minor, int release, int patch){
		this.major = major;
		this.minor = minor;
		this.release = release;
		this.patch = patch;
	}
	
	
	
	public Integer getMajor() {
		return major;
	}

	public Integer getMinor() {
		return minor;
	}

	public Integer getPatch() {
		return patch;
	}

	public Integer getRelease() {
		return release;
	}

	@Override
	public String toString() {
		return major + "." + minor + "." + release + "." + patch;
	}
	
	public int compareTo(Version o) {
		if (getMajor() != o.getMajor())
			return getMajor().compareTo(o.getMajor());
		if (getMinor() != o.getMinor())
			return getMinor().compareTo(o.getMinor());
		if (getRelease() != o.getRelease())
			return getRelease().compareTo(o.getRelease());
		if (getPatch() != o.getPatch())
			return getPatch().compareTo(o.getPatch());
		return 0;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Version)
			return this.compareTo((Version) obj) == 0;
		
		return false;
	}
	
	public boolean isSameMajor(Version compareTo){
		return (this.getMajor().equals(compareTo.getMajor()));
	}
	
	public boolean isSameMinor(Version compareTo){
		return (isSameMajor(compareTo) 
				&& this.getMinor().equals(compareTo.getMinor()));
	}

	public boolean isSameRelease(Version compareTo){
		return (isSameMinor(compareTo) 
				&& this.getRelease().equals(compareTo.getRelease()));		
	}

	public boolean isSamePatch(Version compareTo){
		return (isSameRelease(compareTo) 
				&& this.getPatch().equals(compareTo.getPatch()));
	}

	
	public boolean isGreaterMajor(Version compareTo){
		return (this.getMajor().compareTo(compareTo.getMajor())) > 1;
	}
	
	public boolean isGreaterMinor(Version compareTo){
		return (isGreaterMajor(compareTo) 
				&& (this.getMinor().compareTo(compareTo.getMinor()) > 1));
	}

	public boolean isGreaterRelease(Version compareTo){
		return (isGreaterMinor(compareTo) 
				&& (this.getRelease().compareTo(compareTo.getRelease()) > 1));		
	}

	public boolean isGreaterPatch(Version compareTo){
		return (isGreaterRelease(compareTo) 
				&& (this.getPatch().compareTo(compareTo.getPatch()) > 1));
	}
}