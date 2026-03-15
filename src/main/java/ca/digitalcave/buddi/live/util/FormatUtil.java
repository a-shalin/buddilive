package ca.digitalcave.buddi.live.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ca.digitalcave.buddi.live.model.Source;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

public class FormatUtil {
	public static String HTML_RED = "#dd2222";
	public static String HTML_DISABLED_RED = "#886666";
	public static String HTML_GRAY = "#bbbbbb";
	
	public static String formatDateTimeInternal(Date date){
		if (date == null) return null;
		return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(date);
	}
	public static String formatDateInternal(Date date){
		if (date == null) return null;
		return new SimpleDateFormat("yyyy-MM-dd").format(date);
	}
	public static Date parseDateTimeInternal(String date){
		if (date == null) return null;
		try {
			return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date);
		}
		catch (ParseException e){
			return null;
		}
	}
	public static Date parseDateInternal(String date){
		if (date == null) return null;
		try {
			return new SimpleDateFormat("yyyy-MM-dd").parse(date);
		}
		catch (ParseException e){
			return null;
		}
	}
	
	public static String formatDate(Date date, User user){
		if (date == null) return null;
		return new SimpleDateFormat(user.getDateFormat()).format(date);
	}

	public static BigDecimal parseCurrency(String value){
		if (value == null || value.length() == 0) return null;
		return new BigDecimal(value);
	}
	
	public static String formatCurrency(BigDecimal value, User user){
		if (value == null) return null;

		final BigDecimal absoluteValue = value.abs();
		final Locale locale = user.getLocale() != null ? user.getLocale() : Locale.US;
		final NumberFormat rawFormat = NumberFormat.getNumberInstance(locale);
		final DecimalFormat format = rawFormat instanceof DecimalFormat ? (DecimalFormat) rawFormat : (DecimalFormat) NumberFormat.getNumberInstance();
		final DecimalFormatSymbols symbols = format.getDecimalFormatSymbols();
		symbols.setDecimalSeparator(user.getDecimalSeparator().charAt(0));
		symbols.setGroupingSeparator(user.getThousandSeparator().charAt(0));
		format.setDecimalFormatSymbols(symbols);
		final int fractionDigits = Math.max(0, user.getCurrency() != null ? user.getCurrency().getDefaultFractionDigits() : 2);
		format.setMaximumFractionDigits(fractionDigits);
		format.setMinimumFractionDigits(fractionDigits);
		format.setGroupingUsed(true);

		final String token = user.getCurrencyToken();
		final String amount = format.format(absoluteValue);
		final String spacer = user.useCurrencySpacing() ? " " : "";
		String result = user.isCurrencyAfter()
				? amount + spacer + token
				: token + spacer + amount;

		if (value.compareTo(BigDecimal.ZERO) < 0) {
			if ("B".equals(user.getNegativeFormat())) {
				result = "(" + result + ")";
			}
			else {
				result = "-" + result;
			}
		}

		return result;
	}
	
	public static String formatCurrency(BigDecimal value, User user, Source source){
		if ("C".equals(source.getType()) || "E".equals(source.getType())){
			return formatCurrency(value == null ? null : value.negate(), user);
		}
		return formatCurrency(value, user);
	}
	
	public static String formatRed(){
		return "color: " + FormatUtil.HTML_RED + ";";
	}
	public static String formatGray(){
		return "color: " + FormatUtil.HTML_GRAY + ";";
	}
	
	public static String formatBold(){
		return "font-weight: bold;";
	}
	
	public static boolean isRed(Source selected, User user, Split split) throws CryptoException {
		boolean toSelected = split.getToSource() == selected.getId();
		boolean positive = CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true).compareTo(BigDecimal.ZERO) >= 0;
		if ((!toSelected && positive) || (toSelected && !positive)){
			return true;
		}
		else {
			return false;
		}
	}
	
	public static boolean isRed(Source s, BigDecimal value){
		if (s == null){
			return false;
		}
		if ("D".equals(s.getType()) || "C".equals(s.getType())){
			return value.compareTo(BigDecimal.ZERO) < 0;
		}
		else if ("I".equals(s.getType())){
			return value.compareTo(BigDecimal.ZERO) < 0;
		}
		else if ("E".equals(s.getType())){
			return value.compareTo(BigDecimal.ZERO) >= 0;
		}
		else {
			throw new RuntimeException("Unknown source type '" + s.getType() + "'");
		}
	}
	public static boolean isRed(BigDecimal value){
		if (value == null || value.compareTo(BigDecimal.ZERO) >= 0)
			return false;
		return true;
	}
}
