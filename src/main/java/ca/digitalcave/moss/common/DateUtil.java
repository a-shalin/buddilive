package ca.digitalcave.moss.common;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * @author wyatt
 * A collection of date related functions.  Created from scratch, as a replacement for 
 * DateUtil (from Koders.net) when I changed Moss to include only code which I personally 
 * wrote (and thus have copyright to).
 */
public class DateUtil {
//	private static Calendar calendar = Calendar.getInstance();
	
	/**
	 * Returns the first milisecond in the given date.
	 * @param date
	 * @return
	 */
	public static Date getStartOfDay(final Date date){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.HOUR_OF_DAY, calendar.getActualMinimum(Calendar.HOUR_OF_DAY));
		calendar.set(Calendar.MINUTE, calendar.getActualMinimum(Calendar.MINUTE));
		calendar.set(Calendar.SECOND, calendar.getActualMinimum(Calendar.SECOND));
		calendar.set(Calendar.MILLISECOND, calendar.getActualMinimum(Calendar.MILLISECOND));
		
		return ((Date) calendar.getTime().clone());
	}
	
	/**
	 * Returns the start of the week for the given date.  Whether this is
	 * Sunday or Monday will depend on the current locale. 
	 * @param date
	 * @return
	 */
	public static Date getStartOfWeek(final Date date){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.DAY_OF_WEEK, Calendar.getInstance().getFirstDayOfWeek());
		calendar.set(Calendar.HOUR_OF_DAY, calendar.getActualMinimum(Calendar.HOUR_OF_DAY));
		calendar.set(Calendar.MINUTE, calendar.getActualMinimum(Calendar.MINUTE));
		calendar.set(Calendar.SECOND, calendar.getActualMinimum(Calendar.SECOND));
		calendar.set(Calendar.MILLISECOND, calendar.getActualMinimum(Calendar.MILLISECOND));
		
		return ((Date) calendar.getTime().clone());
	}
	
	/**
	 * Returns the last milisecond in the given date.
	 * @param date
	 * @return
	 */
	public static Date getEndOfDay(final Date date){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.HOUR_OF_DAY, calendar.getActualMaximum(Calendar.HOUR_OF_DAY));
		calendar.set(Calendar.MINUTE, calendar.getActualMaximum(Calendar.MINUTE));
		calendar.set(Calendar.SECOND, calendar.getActualMaximum(Calendar.SECOND));
		calendar.set(Calendar.MILLISECOND, calendar.getActualMaximum(Calendar.MILLISECOND));
		
		return ((Date) calendar.getTime().clone());
	}
	
	/**
	 * Returns the last milisecond in the given date.
	 * @param date
	 * @return
	 */
	public static Date getEndOfWeek(final Date date){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.DAY_OF_WEEK, Calendar.getInstance().getFirstDayOfWeek());
		calendar.set(Calendar.HOUR_OF_DAY, calendar.getActualMaximum(Calendar.HOUR_OF_DAY));
		calendar.set(Calendar.MINUTE, calendar.getActualMaximum(Calendar.MINUTE));
		calendar.set(Calendar.SECOND, calendar.getActualMaximum(Calendar.SECOND));
		calendar.set(Calendar.MILLISECOND, calendar.getActualMaximum(Calendar.MILLISECOND));
		
		return ((Date) getEndOfDay(addDays(calendar.getTime(), 6)).clone());
	}
	
	/**
	 * Returns the date which is offset amount from the given date, at the same time.
	 * @param date
	 * @param offset
	 * @return
	 */
	public static Date addSeconds(final Date date, final int offset){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(Calendar.SECOND, offset);
		
		return ((Date) calendar.getTime().clone());
		
	}
	
	/**
	 * Returns the date which is offset amount from the given date, at the same time.
	 * @param date
	 * @param offset
	 * @return
	 */
	public static Date addMinutes(final Date date, final int offset){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(Calendar.MINUTE, offset);
		
		return ((Date) calendar.getTime().clone());
		
	}
	
	/**
	 * Returns the date which is offset amount from the given date, at the same time.
	 * @param date
	 * @param offset
	 * @return
	 */
	public static Date addHours(final Date date, final int offset){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(Calendar.HOUR_OF_DAY, offset);
		
		return ((Date) calendar.getTime().clone());
		
	}
	
	/**
	 * Returns the date which is offset amount from the given date, at the same time.
	 * @param date
	 * @param offset
	 * @return
	 */
	public static Date addDays(final Date date, final int offset){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(Calendar.DAY_OF_MONTH, offset);
		
		return ((Date) calendar.getTime().clone());
		
	}
	
	/**
	 * Returns the date which is offset amount from the given date, at the same time.  If the day
	 * in the given month is not in the target month, we will adjust the date down.  For instance,
	 * if you call addMonths with the date of January 31 and offset of 1, the result will
	 * be February 28 (or 29 on a leap year), instead of March 2.   
	 * @param date
	 * @param offset
	 * @return
	 */
	public static Date addMonths(final Date date, final int offset){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(Calendar.MONTH, offset);
		
		if (calendar.getActualMaximum(Calendar.DAY_OF_MONTH) < calendar.get(Calendar.DAY_OF_MONTH))
			calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
		
		return ((Date) calendar.getTime().clone());
	}
	
	public static Date addQuarters(final Date date, final int quarterOffset){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(GregorianCalendar.MONTH, quarterOffset * 3); //3 months in a quarter

		return ((Date) calendar.getTime().clone());
	}
	
	/**
	 * Returns the date which is offset amount from the given date, at the same time.  Note that
	 * in the case of a leap year, the day of the month may not be equal between the two.
	 * @param date
	 * @param offset
	 * @return
	 */
	public static Date addYears(final Date date, final int offset){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(Calendar.YEAR, offset);
		
		return ((Date) calendar.getTime().clone());
		
	}

	
	/**
	 * Returns the start of the month of the given date
	 * @param date
	 * @return
	 */
	public static Date getStartOfMonth(final Date date){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMinimum(Calendar.DAY_OF_MONTH));
		calendar.set(Calendar.HOUR_OF_DAY, calendar.getActualMinimum(Calendar.HOUR_OF_DAY));
		calendar.set(Calendar.MINUTE, calendar.getActualMinimum(Calendar.MINUTE));
		calendar.set(Calendar.SECOND, calendar.getActualMinimum(Calendar.SECOND));
		calendar.set(Calendar.MILLISECOND, calendar.getActualMinimum(Calendar.MILLISECOND));
		
		return ((Date) calendar.getTime().clone());
	}
	
	/**
	 * Returns the end of the month of the given date
	 * @param date
	 * @return
	 */
	public static Date getEndOfMonth(final Date date){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
		calendar.set(Calendar.HOUR_OF_DAY, calendar.getActualMaximum(Calendar.HOUR_OF_DAY));
		calendar.set(Calendar.MINUTE, calendar.getActualMaximum(Calendar.MINUTE));
		calendar.set(Calendar.SECOND, calendar.getActualMaximum(Calendar.SECOND));
		calendar.set(Calendar.MILLISECOND, calendar.getActualMaximum(Calendar.MILLISECOND));
		
		return ((Date) calendar.getTime().clone());
	}
	
	/**
	 * Returns the beginning of the quarter.  A quarter is defined as occuring every 3
	 * months, starting on January 1st, then April 1st, etc.  This method calls
	 * startOfDay to ensure that the date object's time is set to 0:00.
	 * @param date The date to use
	 * @param quarterOffset How many quarters forward / back to go.  
	 * Set to 0 to return the quarter for the given date object.
	 * @return
	 */
	public static Date getStartOfQuarter(final Date date){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		int quarterNumber = calendar.get(GregorianCalendar.MONTH) / 3;
		
		if (quarterNumber < 0 || quarterNumber > 3)
			throw new RuntimeException("Error when calculating quarter: date = " + date + " results in quarter = " + quarterNumber);
		
		calendar.set(GregorianCalendar.MONTH, quarterNumber * 3);
		calendar.set(GregorianCalendar.DAY_OF_MONTH, calendar.getActualMinimum(GregorianCalendar.DAY_OF_MONTH));
		return getStartOfDay((Date) calendar.getTime().clone());
	}
	
	/**
	 * Returns the date at the end of the quarter, modified by quarterOffset.
	 * @param date Date to use
	 * @param quarterOffset How many quarters before (negative) / after (positive) to use.
	 * @return
	 */
	public static Date getEndOfQuarter(final Date date){
		return getEndOfDay(addDays(addQuarters(getStartOfQuarter(date), 1), -1));
	}
	
	/**
	 * Returns the start of the year of the given date
	 * @param date
	 * @return
	 */
	public static Date getStartOfYear(final Date date){
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.MONTH, 0);
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		
		return ((Date) calendar.getTime().clone());
	}
	
	/**
	 * Returns the end of the year of the given date
	 * @param date
	 * @return
	 */
	public static Date getEndOfYear(final Date date) {
		if (date == null)
			return null;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.MONTH, calendar.getActualMaximum(Calendar.MONTH));
		calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
		calendar.set(Calendar.HOUR_OF_DAY, calendar.getActualMaximum(Calendar.HOUR_OF_DAY));
		calendar.set(Calendar.MINUTE, calendar.getActualMaximum(Calendar.MINUTE));
		calendar.set(Calendar.SECOND, calendar.getActualMaximum(Calendar.SECOND));
		calendar.set(Calendar.MILLISECOND, calendar.getActualMaximum(Calendar.MILLISECOND));
		
		return ((Date) calendar.getTime().clone());
	}
	
	
	/**
	 * Returns the number of months between the two given dates.  This is defined as 
	 * endDate.month - startDate.month for months in the same year.  For inclusive bit set,
	 * return the result + 1 (or result - 1 if first is after last).
	 * 
	 * If first is after last, it will return a negative number.
	 * 
	 * If either date is null, returns 0.
	 * @param first
	 * @param last
	 * @param inclusive
	 * @return
	 */
	public static int getMonthsBetween(final Date first, final Date last, final boolean inclusive) {
		if (first == null || last == null)
			return 0;

		int firstYear = getYear(first);
		int firstMonth = getMonth(first);
		int lastYear = getYear(last);
		int lastMonth = getMonth(last);
		
		return lastMonth - firstMonth + (12 * (lastYear - firstYear)) + (inclusive ? (first.compareTo(last) > 0 ? 1 : -1) : 0);
	}
	
	/**
	 * Returns the number of days between the two given dates.  This is defined as 
	 * endDate.day - startDate.day for days in the same month.  For inclusive bit set,
	 * return the result + 1 (or result - 1 if first is after last).
	 * 
	 * If either date is null, returns 0.
	 * 
	 * If first is after last, it will return a negative number. 
	 * @param first
	 * @param last
	 * @param inclusive
	 * @return
	 */
	public static int getDaysBetween(final Date first, final Date last, final boolean inclusive) {
		if (first == null || last == null)
			return 0;

		Calendar cFirst = Calendar.getInstance();
		Calendar cLast = Calendar.getInstance();
		
		cFirst.setTime(first);
		cLast.setTime(last);

		int daysBetween;
		if (cFirst.get(Calendar.YEAR) == cLast.get(Calendar.YEAR)){
			daysBetween = cLast.get(Calendar.DAY_OF_YEAR) - cFirst.get(Calendar.DAY_OF_YEAR);
		}
		else {
			Calendar cTemp;
			boolean swap = false;
			if (cFirst.get(Calendar.YEAR) > cLast.get(Calendar.YEAR)){
				//We are now guaranteed that cFirst is befire cLast.
				swap = true;
				cTemp = cFirst;
				cFirst = cLast;
				cLast = cTemp;
			}

			cTemp = Calendar.getInstance();
			cTemp.setTime(cFirst.getTime());
			cTemp.set(Calendar.DAY_OF_YEAR, cTemp.getActualMaximum(Calendar.DAY_OF_YEAR));
			int daysAfterFirstDateInYear = getDaysBetween(cFirst.getTime(), cTemp.getTime(), true);

			cTemp = Calendar.getInstance();
			cTemp.setTime(cLast.getTime());
			cTemp.set(Calendar.DAY_OF_YEAR, cTemp.getActualMinimum(Calendar.DAY_OF_YEAR));
			int daysBeforeLastDateInYear = getDaysBetween(cTemp.getTime(), cLast.getTime(), false);
			
			int daysInCompleteYearsBetweenDates = 0;
			for (int i = cFirst.get(Calendar.YEAR) + 1; i < cLast.get(Calendar.YEAR); i++){
				cTemp.set(Calendar.YEAR, i);
				daysInCompleteYearsBetweenDates += cTemp.getActualMaximum(Calendar.DAY_OF_YEAR);
			}

			daysBetween = daysAfterFirstDateInYear + daysInCompleteYearsBetweenDates + daysBeforeLastDateInYear;
			if (swap)
				daysBetween *= -1;
		}

		int adjustment = 0;
		if (inclusive){
			if (first.equals(last))
				adjustment = 1;
			else if (first.before(last))
				adjustment = 1;
			else
				adjustment = -1;
		}
	
	return (int) daysBetween + adjustment;
		
	}
//	public static int getDaysBetween(Date first, Date last, boolean inclusive) {
//		if (first == null || last == null)
//			return 0;
//		if (isSameDay(first, last))
//			return 0 + (inclusive ? 1 : 0);
//		long difference = last.getTime() - first.getTime();
//		final long milisecondsInDay = 1000 * 60 * 60 * 24;
//		int adjustment = 0;
//		long daysBetween = Math.abs(Math.round((double) difference / milisecondsInDay));
//			
//		if (inclusive){
//			if (first.equals(last))
//				adjustment = 1;
//			else if (first.before(last))
//				adjustment = 1;
//			else
//				adjustment = -1;
//		}
//		
//		return (int) daysBetween + adjustment;
//	}
	
	/**
	 * Return the year for the given date.  If date is null, return 0.
	 * @param date
	 * @return
	 */
	public static int getYear(final Date date){
		if (date == null)
			return 0;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		
		return calendar.get(Calendar.YEAR);
	}

	/**
	 * Return the month for the given date. January is 0, etc.  If date is null, return -1.  
	 * @param date
	 * @return
	 */
	public static int getMonth(final Date date){
		if (date == null)
			return -1;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		
		return calendar.get(Calendar.MONTH);
	}

	
	/**
	 * Return the day of the month for the given date.  If date is null, return 0.
	 * @param date
	 * @return
	 */
	public static int getDay(final Date date){
		if (date == null)
			return 0;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		
		return calendar.get(Calendar.DAY_OF_MONTH);
	}
	
	/**
	 * Return the hour (in 24 hour format).  If date is null, return -1
	 * @param date
	 * @return
	 */
	public static int getHour(final Date date){
		if (date == null)
			return -1;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		
		return calendar.get(Calendar.HOUR_OF_DAY);
	}

	/**
	 * Return the minute for the given date.  If date is null, return -1.
	 * @param date
	 * @return
	 */
	public static int getMinute(final Date date){
		if (date == null)
			return -1;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		
		return calendar.get(Calendar.MINUTE);
	}

	/**
	 * Return the second for the given date.  If date is null, return -1.
	 * @param date
	 * @return
	 */
	public static int getSecond(final Date date){
		if (date == null)
			return -1;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		
		return calendar.get(Calendar.SECOND);
	}

	/**
	 * Return a date object at the beginning of the given day.
	 * @param year
	 * @param month
	 * @param day
	 * @return
	 */
	public static Date getDate(final int year, final int month, final int day){
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.YEAR, year);
		calendar.set(Calendar.MONTH, month);
		calendar.set(Calendar.DAY_OF_MONTH, day);
		
		return getStartOfDay(calendar.getTime());
	}
	
	/**
	 * Return a date object at the beginning of the given month. 
	 * @param year
	 * @param month
	 * @return
	 */
	public static Date getDate(final int year, final int month){
		return getStartOfMonth(getDate(year, month, 1));
	}
	
	/**
	 * Return a date object at the beginning of the given year. 
	 * @param year
	 * @return
	 */
	public static Date getDate(final int year){
		return getStartOfYear(getDate(year, Calendar.JANUARY, 1));
	}
	
	/**
	 * Checks if the two given dates are the same day.  If either date is null,
	 * returns false.
	 * @param d1
	 * @param d2
	 * @return
	 */
	public static boolean isSameDay(final Date d1, final Date d2){
		if (d1 == null || d2 == null)
			return false;
		//Since the vast majority of dates compared will return false, we have a simple
		// check which will eliminate most of the further checks.
		if (d1.getTime() < d2.getTime() - 86400000 
				|| d2.getTime() < d1.getTime() - 86400000)
			return false;
		if (getDay(d1) != getDay(d2))
			return false;
		return true;
	}
	
	/**
	 * Checks if two dates exist in the same calendar week.  Whether Sunday or Monday
	 * is defined as the start of the week depends on the locale.
	 * @param d1
	 * @param d2
	 * @return
	 */
	public static boolean isSameWeek(final Date d1, final Date d2){
		return getStartOfWeek(d1).getTime() == getStartOfWeek(d2).getTime();
	}
	
	/**
	 * Checks if the two given dates are the same month.  If either date is null, returns false. 
	 * @param d1
	 * @param d2
	 * @return
	 */
	public static boolean isSameMonth(final Date d1, final Date d2){
		if (d1 == null || d2 == null)
			return false;
		if (d1.getTime() < d2.getTime() - 2764800000l 
				|| d2.getTime() < d1.getTime() - 2764800000l)
			return false;
		
//		if (getYear(d1) != getYear(d2))
//			return false;
		if (getMonth(d1) != getMonth(d2))
			return false;
		return true;
	}
	
	/**
	 * Checks if the two given dates are the same year.  If either date is null, returns false.
	 * @param d1
	 * @param d2
	 * @return
	 */
	public static boolean isSameYear(final Date d1, final Date d2){
		if (d1 == null || d2 == null)
			return false;
		if (getYear(d1) != getYear(d2))
			return false;
		return true;
	}
	
	/**
	 * Returns the number of days in the month, for the date given.  If date is null, return -1.
	 * @param date
	 * @return
	 */
	public static int getDaysInMonth(final Date date){
		if (date == null)
			return -1;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return calendar.getActualMaximum(Calendar.DATE);
	}
	
	/**
	 * Returns the number of days in the year, for the date given.  If date is null, return -1.
	 * @param date
	 * @return
	 */
	public static int getDaysInYear(Date date){
		if (date == null)
			return -1;
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return calendar.getActualMaximum(Calendar.DAY_OF_YEAR);
	}
	
	/**
	 * Changes the internal time of the given Date to be set to the value obtained
	 * from DateFunctions.getDate(year, month, day). 
	 * @param date
	 * @param year
	 * @param month
	 * @param day
	 */
	public static void setDate(Date date, int year, int month, int day){
		date.setTime(getDate(year, month, day).getTime());
	}
}
