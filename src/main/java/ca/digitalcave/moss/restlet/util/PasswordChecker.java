package ca.digitalcave.moss.restlet.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.solinger.cracklib.CrackLib;
import org.solinger.cracklib.Packer;

import ca.digitalcave.moss.crypto.DefaultHash;

public class PasswordChecker {
	private String patternsPath;
	private List<Pattern> patterns;
	private String dictionaryPath;
	private Packer packer;
	private boolean dictionaryEnforced = true;
	private boolean historyEnforced = false;
	private boolean patternsEnforced = false;
	private boolean customEnforced = false;
	private int minimumStrength = 30;
	private int minimumLength = 8;
	private int minimumVariance = 5;
	private int minimumClasses = 2;

	public boolean test(String identifier, String password) {
		return isValid(identifier, password);
	}
	public boolean isValid(String identifier, String password) {
		return testLength(password) 
				&& testStrength(password) 
				&& testVariance(password) 
				&& testMulticlass(password) 
				&& testDictionary(password) 
				&& testHistory(identifier, password) 
				&& testPatterns(password) 
				&& testCustom(identifier, password);
	}

	/**
	 * Returns a code with the given strength scale:
	 * <ul>
	 * <li>0-19 very poor</li>
	 * <li>20-39 weak</li>
	 * <li>40-59 is average</li>
	 * <li>60-79 is strong</li>
	 * <li>80+ is excellent</li>
	 * <li>@param password</li>
	 * @return
	 */
	public int getStrengthScore(String password) {
		float factor = 0;

		if (hasLower(password)) factor += 2.6f;
		if (hasUpper(password)) factor += 2.6f;
		if (hasNumber(password)) factor += 1.0f;
		if (hasSpace(password)) factor += 0.1f;
		if (hasNumberSymbol(password)) factor += 1.0f;
		if (hasOther(password)) factor += 2.2f;

		return (int) (Math.pow(password.length(), 3) * factor / 100f);
	}

	public String getStrenthString(String password) {
		int strength = getStrengthScore(password);
		if (strength >= 80) return "Excellent";
		if (strength >= 60) return "Strong";
		if (strength >= 40) return "Average";
		if (strength >= 20) return "Weak";
		return "Very Poor";
	}

	public boolean hasLower(String password) {
		return password.matches(".*[a-z].*"); 
	}
	public boolean hasUpper(String password) {
		return password.matches(".*[A-Z].*");
	}
	public boolean hasNumber(String password) {
		return password.matches(".*[0-9].*");
	}
	public boolean hasSpace(String password) {
		return password.matches(".*[ ].*");
	}
	public boolean hasNumberSymbol(String password) {
		return password.matches(".*[!@#$%^&*()].*");
	}
	public boolean hasOther(String password) {
		return password.matches("/.*[^ a-zA-Z0-9!@#$%^&*()].*/");
	}
	public boolean hasSymbol(String password) {
		return hasNumberSymbol(password) || hasOther(password);
	}

	public boolean testMulticlass(String password) {
		return minimumClasses == 0 || !isNotMulticlass(password) ? true : false;
	}
	public boolean isNotMulticlass(String password) {
		return getClasses(password) < minimumClasses;
	}

	public int getClasses(String password) {
		int classes = 0;
		if (hasLower(password)) classes++;
		if (hasUpper(password)) classes++;
		if (hasNumber(password)) classes++;
		if (hasSpace(password)) classes++;
		if (hasNumberSymbol(password)) classes++;
		if (hasOther(password)) classes++;
		return classes; 
	}

	public boolean testStrength(String password) {
		return minimumStrength == 0 || !isWeak(password) ? true : false;
	}
	public boolean isWeak(String password) {
		return getStrengthScore(password) < getMinimumStrength();
	}

	public boolean testLength(String password) {
		return minimumLength == 0 || !isShort(password) ? true : false;
	}
	public boolean isShort(String password) {
		return password.length() < getMinimumLength();
	}

	public boolean testVariance(String password) {
		return minimumVariance == 0 || !isUnvaried(password) ? true : false;
	}
	public boolean isUnvaried(String password) {
		if (password == null || password.length() == 0) return true;
		String chars = new String(password.substring(0,1));

		for (int i = 1; i < password.length(); i++) { 
			if (chars.indexOf(password.charAt(i)) == -1) {
				chars = chars +password.charAt(i);
			}
		}
		return chars.length() < minimumVariance;
	}

	public boolean testDictionary(String password) {
		return !dictionaryEnforced || !isInDictionary(password) ? true : false;
	}
	public synchronized boolean isInDictionary(String password) {
		if (packer == null) {
			try {
				initDictionary();
			} catch (IOException e) {
				Logger.getLogger(PasswordChecker.class.getName()).log(Level.WARNING, "Unable to initialize dictionary", e);
				return false; // it's their lucky day
			}
		}
		try {
			if (password.length() < 6) return false;	//Cracklib does not work on (some) 5 char or shorter words
			return CrackLib.find(packer, password) != null;
		} catch (Throwable e) {
			Logger.getLogger(PasswordChecker.class.getName()).log(Level.WARNING, "Unable to check dictionary", e);
			return false; // it's their lucky day
		}
	}

	/**
	 * This method should be implemented in a subclass if history checking is required. 
	 */
	protected boolean verifyHash(String hash, String password) {
		try {
			return DefaultHash.verify(hash, password);
		}
		catch (Throwable e){
			return false;	//Old hashing schemes cause this to break.  Catch errors and assume that the errors do not match.
		}
	}

	/**
	 * This method should be implemented in a subclass if history checking is required. 
	 */
	protected List<String> getHistory(String identifier) {
		return Collections.emptyList();
	}

	public boolean testHistory(String identifier, String password) {
		return !historyEnforced || !isInHistory(identifier, password) ? true : false; 
	}
	public boolean isInHistory(String identifier, String password) {
		for(String hash : getHistory(identifier)){
			if (verifyHash(hash, password)) return true;
		}
		return false;
	}

	public boolean testPatterns(String password) {
		if (patterns == null) {
			try {
				initPatterns();
			} catch (IOException e) {
				Logger.getLogger(PasswordChecker.class.getName()).log(Level.WARNING, "Unable to initialize patterns", e);
				return false; // it's their lucky day
			}
		}
			
		return !patternsEnforced || !isRestricted(password) ? true : false;
	}
	public boolean isRestricted(String password) {
		for (Pattern pattern : getRestrictedPatterns()) {
			if (pattern.matcher(password).find()) {
				return true;
			}
		}
		return false;
	}

	public boolean testCustom(String identifier, String password) {
		return !customEnforced || !isCustom(identifier, password) ? true : false;
	}

	/**
	 * This method should be implemented in a subclass if other custom checks are required. 
	 */
	public boolean isCustom(String identifier, String password) {
		return false;
	}

	public List<Pattern> getRestrictedPatterns() {
		return patterns;
	}
	public PasswordChecker setRestrictedPatterns(List<Pattern> patterns) {
		this.patterns = patterns;
		return this;
	}

	public int getMinimumLength() {
		return minimumLength;
	}
	public PasswordChecker setMinimumLength(int minimumLength) {
		this.minimumLength = minimumLength;
		return this;
	}

	public int getMinimumStrength() {
		return minimumStrength;
	}
	public PasswordChecker setMinimumStrength(int minimumStrength) {
		this.minimumStrength = minimumStrength;
		return this;
	}
	
	public int getMinimumVariance() {
		return minimumVariance;
	}
	public PasswordChecker setMinimumVariance(int minimumVariance) {
		this.minimumVariance = minimumVariance;
		return this;
	}
	
	public int getMinimumClasses() {
		return minimumClasses;
	}
	public PasswordChecker setMinimumClasses(int minimumClasses) {
		this.minimumClasses = minimumClasses;
		return this;
	}

	public boolean isLengthEnforced() {
		return minimumLength > 0;
	}
	public boolean isStrengthEnforced() {
		return minimumStrength > 0;
	}
	public boolean isVarianceEnforced() {
		return minimumVariance > 0;
	}
	public boolean isMultiClassEnforced() {
		return minimumClasses > 0;
	}
	
	public boolean isDictionaryEnforced() {
		return dictionaryEnforced;
	}
	public PasswordChecker setDictionaryEnforced(boolean dictionaryEnforced) {
		this.dictionaryEnforced = dictionaryEnforced;
		return this;
	}

	public boolean isHistoryEnforced() {
		return historyEnforced;
	}
	public PasswordChecker setHistoryEnforced(boolean historyEnforced) {
		this.historyEnforced = historyEnforced;
		return this;
	}

	public boolean isPatternsEnforced() {
		return patternsEnforced;
	}
	public PasswordChecker setPatternsEnforced(boolean patternsEnforced) {
		this.patternsEnforced = patternsEnforced;
		return this;
	}

	public boolean isCustomEnforced() {
		return customEnforced;
	}
	public PasswordChecker setCustomEnforced(boolean customEnforced) {
		this.customEnforced = customEnforced;
		return this;
	}
	
	public PasswordChecker setDictionaryPath(String dictionaryPath) {
		this.dictionaryPath = dictionaryPath;
		try {
			initDictionary();
		} catch (IOException e) {
			Logger.getLogger(PasswordChecker.class.getName()).log(Level.WARNING, "Unable to initialize dictionary", e);
		}
		return this;
	}
	public String getDictionaryPath() {
		return dictionaryPath;
	}
	
	public PasswordChecker setPatternsPath(String patternsPath) {
		this.patternsPath = patternsPath;
		try {
			initPatterns();
		} catch (IOException e) {
			Logger.getLogger(PasswordChecker.class.getName()).log(Level.WARNING, "Unable to initialize patterns", e);
		}
		return this;
	}
	
	protected void initDictionary() throws IOException {
		if (this.dictionaryPath == null || this.dictionaryPath.trim().length() == 0) {
			initDefaultDictionary();
		} else {
			initDictionary(this.dictionaryPath);
		}
	}
	protected void initDefaultDictionary() throws IOException {
		initDictionary(new InputStreamReader(PasswordChecker.class.getResourceAsStream("words.txt")));
	}
	protected void initDictionary(String path) throws IOException {
		initDictionary(new FileReader(path));
	}
	protected synchronized void initDictionary(Reader reader) throws IOException {
		if (this.packer != null) {
			this.packer.close();
			this.packer = null;
		}
		
		final File file = new File(System.getProperty("java.io.tmpdir"), "words");
		final Packer p = new Packer(file.getAbsolutePath(),"rw");
		try {
			final BufferedReader br = new BufferedReader(reader);
			String s = null;
			while ((s = br.readLine()) != null) {
				p.put(s);
			}
		} finally {
			reader.close();
			p.close();
		}
		
		this.packer = new Packer(file.getAbsolutePath(), "r");
	}
	
	protected void initPatterns() throws IOException {
		if (this.patternsPath == null || this.patternsPath.trim().length() == 0) {
			initDefaultPatterns();
		} else {
			initPatterns(this.patternsPath);
		}
	}
	protected void initDefaultPatterns() {
		this.patterns = new LinkedList<Pattern>();
	}
	protected void initPatterns(String path) throws IOException {
		initPatterns(new FileReader(path));
	}
	protected synchronized void initPatterns(Reader reader) throws IOException {
		if (this.patterns != null) {
			this.patterns.clear();
		}
		this.patterns = new LinkedList<Pattern>();
		
		try {
			final BufferedReader br = new BufferedReader(reader);
			String s = null;
			while ((s = br.readLine()) != null) {
				try {
					patterns.add(Pattern.compile(s));
				} catch (Throwable t) {
					Logger.getLogger(PasswordChecker.class.getName()).log(Level.WARNING, "Ignoring bad pattern: " + s);
				}
			}
		} finally {
			reader.close();
		}
	}
}