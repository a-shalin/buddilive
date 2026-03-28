package ca.digitalcave.buddi.live.model;

import ca.digitalcave.moss.auth.model.AuthUser;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.SecretKey;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Currency;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class User extends AuthUser {
	private static final long serialVersionUID = 1L;
	private static final Map<String, String> CURRENCY_SYMBOLS_BY_CODE = new ConcurrentHashMap<String, String>();
	
	private String plaintextIdentifier;	//Not persisted, injected by BuddiVerifier
	private String plaintextSecret;	//Not persisted, injected by BuddiVerifier
	private String encryptionKey;
//	private String decryptedEncryptionKey;	//Not persisted, injected by BuddiVerifier; deprecated.  Once all users are off of encryption version 1, we can delete this.
	private SecretKey decryptedSecretKey;	//Not persisted, injected by BuddiVerifier
	private String uuid;
	private Boolean premium = false;
	private Locale locale;
	private Currency currency;
	private String overrideDateFormat;
	private String overrideCurrencyAfter;
	private String overrideDecimalSeparator;
	private String overrideThousandsSeparator;
	private String overrideNegativeFormat;
	private Boolean showCurrencySymbol;
	private String currencySpacing;
	private Boolean showCleared;
	private Boolean showReconciled;
	private Boolean showDeleted;
	private Date created;
	private Date modified;

	public String getUuid() {
		return uuid;
	}
	public void setUuid(final String uuid) {
		this.uuid = uuid;
	}
	public void setSecretString(final String secret) {
		setSecret(secret == null ? null : secret.toCharArray());
	}
	public String getSecretString() {
		return getSecret() == null ? null : new String(getSecret());
	}
	public String getPlaintextIdentifier() {
		return plaintextIdentifier;
	}
	public void setPlaintextIdentifier(final String plaintextIdentifier) {
		this.plaintextIdentifier = plaintextIdentifier;
	}
	public String getPlaintextSecret() {
		return plaintextSecret;
	}
	public void setPlaintextSecret(final String plaintextSecret) {
		this.plaintextSecret = plaintextSecret;
	}
	public String getEncryptionKey() {
		return encryptionKey;
	}
	public void setEncryptionKey(final String encryptionKey) {
		this.encryptionKey = encryptionKey;
//		decryptedEncryptionKey = null;
		decryptedSecretKey = null;
	}
//	public String getDecryptedEncryptionKey() throws CryptoException {
//		if (decryptedEncryptionKey == null && isEncrypted()) {
//			decryptedEncryptionKey = Crypto.decrypt(plaintextSecret, encryptionKey);
//		}
//		return decryptedEncryptionKey;
//	}
	public SecretKey getDecryptedSecretKey() throws CryptoException {
		if (decryptedSecretKey == null && isEncrypted()){
			decryptedSecretKey = Crypto.recoverSecretKey(Crypto.decrypt(plaintextSecret, encryptionKey));
		}
		return decryptedSecretKey;
	}
	public boolean isEncrypted(){
		return encryptionKey != null;
	}
	public Date getCreated() {
		return created;
	}
	public void setCreated(final Date created) {
		this.created = created;
	}

	public Date getModified() {
		return modified;
	}
	public void setModified(final Date modified) {
		this.modified = modified;
	}
	
	public boolean isPremium() {
		return premium;
	}
	public void setPremium(final boolean premium) {
		this.premium = premium;
	}
	public Locale getLocale() {
		return locale;
	}
	public void setLocale(final Locale locale) {
		this.locale = locale;
	}
	public String getExtDateFormat(){
		//Auto converts from Java format to EXT JS (PHP) format.
		//TODO This may need tweaking for accuracy and performance.
		return getDateFormat()
				.replaceAll("yyyy", "Y")
				.replaceAll("yy", "y")
				.replaceAll("ddd", "D")
				.replaceAll("dd?", "d")
				//.replaceAll("([^M]?)M([^M]?)", "$1n$2")		//Single M should be replaced with non-leading zero month
				.replaceAll("MMMM", "F")
				.replaceAll("MMM", "M")
				.replaceAll("MM?", "m");
	}
	public String getDateFormat() {
		if (StringUtils.isBlank(overrideDateFormat)) {
			if (locale != null) {
				final DateFormat format = DateFormat.getDateInstance(DateFormat.SHORT, locale);
				if (format instanceof SimpleDateFormat) return ((SimpleDateFormat) format).toLocalizedPattern();
			} 
		}
		else {
			try {
				if (new SimpleDateFormat(overrideDateFormat) != null);
				return overrideDateFormat;
			}
			catch (IllegalArgumentException e){}
		}
		
		return "yyyy-MM-dd";
	}
	public String getOverrideDateFormat() {
		return overrideDateFormat;
	}
	public void setOverrideDateFormat(final String overrideDateFormat) {
		this.overrideDateFormat = overrideDateFormat;
	}
	public Currency getCurrency() {
		return currency;
	}
	public void setCurrency(final Currency currency) {
		this.currency = currency;
	}
	public String getCurrencySymbol(){
		return getCurrencyToken();
	}
	public String getCurrencyToken(){
		if (currency == null) return "";
		if (isShowCurrencySymbol()) {
			return resolveCurrencySymbol(currency);
		}
		return currency.getCurrencyCode();
	}

	private static String resolveCurrencySymbol(final Currency currency) {
		final String currencyCode = currency.getCurrencyCode();
		return CURRENCY_SYMBOLS_BY_CODE.computeIfAbsent(currencyCode, code -> {
			String bestSymbol = null;
			for (final Locale candidateLocale : Locale.getAvailableLocales()) {
				try {
					final Currency candidateCurrency = Currency.getInstance(candidateLocale);
					if (!currency.equals(candidateCurrency)) continue;
					final String symbol = currency.getSymbol(candidateLocale);
					if (isCurrencySymbol(symbol, code)) {
						if (bestSymbol == null || symbol.length() < bestSymbol.length()) {
							bestSymbol = symbol;
							if (bestSymbol.length() == 1) {
								break;
							}
						}
					}
				}
				catch (IllegalArgumentException e) {}
			}

			if (bestSymbol != null) {
				return bestSymbol;
			}

			final String defaultSymbol = currency.getSymbol();
			return isCurrencySymbol(defaultSymbol, code) ? defaultSymbol : code;
		});
	}

	private static boolean isCurrencySymbol(final String symbol, final String currencyCode) {
		return symbol != null && symbol.length() > 0 && !currencyCode.equals(symbol);
	}

	public boolean isCurrencyAfter() {
		if ("Y".equals(overrideCurrencyAfter)) return true;
		if ("N".equals(overrideCurrencyAfter)) return false;
		if (currency != null) {
			final DecimalFormat format = (DecimalFormat) NumberFormat.getCurrencyInstance(getEffectiveLocale());
			format.setCurrency(currency);
			final String pattern = format.toPattern();
			final int currencyPosition = pattern.indexOf('\u00a4');
			final int numberPosition = pattern.indexOf('#');
			if (currencyPosition >= 0 && numberPosition >= 0) {
				return currencyPosition > numberPosition;
			}
		}
		return false;
	}
	public String getOverrideCurrencyAfter() {
		return overrideCurrencyAfter;
	}
	public void setOverrideCurrencyAfter(final String overrideCurrencyAfter) {
		this.overrideCurrencyAfter = overrideCurrencyAfter;
	}
	public String getOverrideDecimalSeparator() {
		return overrideDecimalSeparator;
	}
	public void setOverrideDecimalSeparator(final String overrideDecimalSeparator) {
		this.overrideDecimalSeparator = (overrideDecimalSeparator == null || overrideDecimalSeparator.length() == 0) ? null : overrideDecimalSeparator.substring(0, 1);
	}
	public String getOverrideThousandsSeparator() {
		return overrideThousandsSeparator;
	}
	public void setOverrideThousandsSeparator(final String overrideThousandsSeparator) {
		this.overrideThousandsSeparator = (overrideThousandsSeparator == null || overrideThousandsSeparator.length() == 0) ? null : overrideThousandsSeparator.substring(0, 1);
	}
	public String getOverrideNegativeFormat() {
		return overrideNegativeFormat;
	}
	public void setOverrideNegativeFormat(final String overrideNegativeFormat) {
		this.overrideNegativeFormat = "B".equals(overrideNegativeFormat) ? "B" : "N";
	}
	public String getNegativeFormat() {
		return "B".equals(overrideNegativeFormat) ? "B" : "N";
	}
	public boolean isShowCurrencySymbol() {
		return showCurrencySymbol != null && showCurrencySymbol;
	}
	public void setShowCurrencySymbol(final Boolean showCurrencySymbol) {
		this.showCurrencySymbol = showCurrencySymbol;
	}
	public String getCurrencySpacing() {
		return currencySpacing;
	}
	public void setCurrencySpacing(final String currencySpacing) {
		this.currencySpacing = (currencySpacing == null || currencySpacing.length() == 0) ? null : ("Y".equals(currencySpacing) ? "Y" : "N");
	}
	public boolean useCurrencySpacing() {
		if ("Y".equals(currencySpacing)) return true;
		if ("N".equals(currencySpacing)) return false;
		return !isShowCurrencySymbol();
	}
	public boolean isShowCleared() {
		return showCleared;
	}
	public void setShowCleared(final boolean showCleared) {
		this.showCleared = showCleared;
	}
	public boolean isShowDeleted() {
		return showDeleted;
	}
	public void setShowDeleted(final boolean showDeleted) {
		this.showDeleted = showDeleted;
	}
	public boolean isShowReconciled() {
		return showReconciled;
	}
	public void setShowReconciled(final boolean showReconciled) {
		this.showReconciled = showReconciled;
	}
	public String getDecimalSeparator(){
		if (overrideDecimalSeparator != null) return overrideDecimalSeparator;
		return ((DecimalFormat) NumberFormat.getInstance(getEffectiveLocale())).getDecimalFormatSymbols().getDecimalSeparator() + "";
	}
	public String getThousandSeparator(){
		if (overrideThousandsSeparator != null) return overrideThousandsSeparator;
		return ((DecimalFormat) NumberFormat.getInstance(getEffectiveLocale())).getDecimalFormatSymbols().getGroupingSeparator() + "";
	}
	private Locale getEffectiveLocale() {
		return locale != null ? locale : Locale.US;
	}
}
