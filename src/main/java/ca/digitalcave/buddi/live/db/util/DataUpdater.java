package ca.digitalcave.buddi.live.db.util;

import ca.digitalcave.buddi.live.db.*;
import ca.digitalcave.buddi.live.model.*;
import ca.digitalcave.buddi.live.model.ScheduledTransaction.ScheduleFrequency;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.moss.common.DateUtil;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.util.*;

public class DataUpdater {

	public static void updateBalances(final User user, final Sources sources, final Transactions transactions, final Crypto crypto) throws DatabaseException, CryptoException {
		//Look through all splits in all accounts.  Start with the earliest split in each account.  If we find a
		// split which does not have the correct balance, we update it; if the balance is already good, leave it alone.
		final List<Split> splits = transactions.selectSplits(user);
		final Map<Integer, List<Split>> splitsByAccount = new HashMap<Integer, List<Split>>();
		for (Split split : splits) {
			if ("C".equals(split.getFromType()) || "D".equals(split.getFromType())) {
				final int accountId = split.getFromSource();
				if (splitsByAccount.get(accountId) == null) splitsByAccount.put(accountId, new ArrayList<Split>());
				splitsByAccount.get(accountId).add(split);
			}
			if ("C".equals(split.getToType()) || "D".equals(split.getToType())) {
				final int accountId = split.getToSource();
				if (splitsByAccount.get(accountId) == null) splitsByAccount.put(accountId, new ArrayList<Split>());
				splitsByAccount.get(accountId).add(split);
			}
		}

		final List<Account> accounts = sources.selectAccounts(user);
		for (Account account : accounts) {
			if (splitsByAccount.get(account.getId()) == null) splitsByAccount.put(account.getId(), new ArrayList<Split>());
			BigDecimal previousBalance = CryptoUtil.decryptWrapperBigDecimal(account.getStartBalance(), user, true);
			BigDecimal newBalance = previousBalance;
			int count = 0;
			for (Split split : splitsByAccount.get(account.getId())) {
				if (split.getFromSource() == account.getId()) {
					newBalance = previousBalance.subtract(CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true));
					final BigDecimal splitFromBalance = CryptoUtil.decryptWrapperBigDecimal(split.getFromBalance(), user, false);
					if (splitFromBalance == null || splitFromBalance.compareTo(newBalance) != 0) {
						final String encodedNewBalance = user.isEncrypted() ? crypto.encrypt(user.getDecryptedSecretKey(), newBalance.toPlainString()) : newBalance.toPlainString();
						count = transactions.updateSplitBalance(user, split.getId(), encodedNewBalance, true);
						if (count != 1) throw new DatabaseException("Expected 1 split row updated; returned " + count);
					}
				}
				else if (split.getToSource() == account.getId()) {
					newBalance = previousBalance.add(CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true));
					final BigDecimal splitToBalance = CryptoUtil.decryptWrapperBigDecimal(split.getToBalance(), user, false);
					if (splitToBalance == null || splitToBalance.compareTo(newBalance) != 0) {
						final String encodedNewBalance = user.isEncrypted() ? crypto.encrypt(user.getDecryptedSecretKey(), newBalance.toPlainString()) : newBalance.toPlainString();
						count = transactions.updateSplitBalance(user, split.getId(), encodedNewBalance, false);
						if (count != 1) throw new DatabaseException("Expected 1 split row updated; returned " + count);
					}
				}
				else {
					throw new DatabaseException("This should never happen");
				}

				previousBalance = newBalance;
			}

			if (newBalance.compareTo(CryptoUtil.decryptWrapperBigDecimal(account.getBalance(), user, true)) != 0) {
				final String encodedNewBalance = user.isEncrypted() ? crypto.encrypt(user.getDecryptedSecretKey(), newBalance.toPlainString()) : newBalance.toPlainString();
				count = sources.updateAccountBalance(user, account.getId(), encodedNewBalance);
				if (count != 1) throw new DatabaseException("Expected 1 account row updated; returned " + count);
			}
		}
	}

	public static String updateScheduledTransactions(final User user, final Sources sources, final Transactions transactionsMapper,
			final ScheduledTransactions scheduledTransactionsMapper, final Crypto crypto, final Date userDate) throws CryptoException, DatabaseException {
		final Date today = DateUtil.getEndOfDay(userDate);

		boolean thereWasAnUpate = false;

		final StringBuilder sb = new StringBuilder();

		final GregorianCalendar tempCal = new GregorianCalendar();

		final List<ScheduledTransaction> scheduledTransactions = scheduledTransactionsMapper.selectOustandingScheduledTransactions(user);

		for (ScheduledTransaction scheduledTransaction : scheduledTransactions) {
			Date tempDate = scheduledTransaction.getLastCreatedDate();
			boolean isNewTransaction = false;
			Date lastDayCreated = null;
			if (tempDate == null) {
				tempDate = scheduledTransaction.getStartDate();
				isNewTransaction = true;
				lastDayCreated = DateUtil.getDate(1900);
			}
			else {
				lastDayCreated = DateUtil.getStartOfDay(tempDate);
				tempDate = DateUtil.addDays(tempDate, 1);
			}

			tempDate = DateUtil.getStartOfDay(tempDate);

			while (tempDate.before(today)
					&& (scheduledTransaction.getEndDate() == null
							|| scheduledTransaction.getEndDate().after(tempDate)
							|| (DateUtil.getDaysBetween(scheduledTransaction.getEndDate(), tempDate, false) == 0))) {

				tempCal.setTime(tempDate);

				boolean todayIsTheDay = false;

				if (scheduledTransaction.getFrequencyType().equals(ScheduleFrequency.SCHEDULE_FREQUENCY_MONTHLY_BY_DATE.toString())
						&& (scheduledTransaction.getScheduleDay() == tempCal.get(Calendar.DAY_OF_MONTH)
								|| (scheduledTransaction.getScheduleDay() == 32
										&& tempCal.get(Calendar.DAY_OF_MONTH) == tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)))) {
					todayIsTheDay = true;
				}
				else if (scheduledTransaction.getFrequencyType().equals(ScheduleFrequency.SCHEDULE_FREQUENCY_MONTHLY_BY_DAY_OF_WEEK.toString())
						&& scheduledTransaction.getScheduleDay() + 1 == tempCal.get(Calendar.DAY_OF_WEEK)
						&& tempCal.get(Calendar.DAY_OF_MONTH) <= 7) {
					todayIsTheDay = true;
				}
				else if (scheduledTransaction.getFrequencyType().equals(ScheduleFrequency.SCHEDULE_FREQUENCY_WEEKLY.toString())
						&& scheduledTransaction.getScheduleDay() + 1 == tempCal.get(Calendar.DAY_OF_WEEK)) {
					todayIsTheDay = true;
				}
				else if (scheduledTransaction.getFrequencyType().equals(ScheduleFrequency.SCHEDULE_FREQUENCY_BIWEEKLY.toString())
						&& scheduledTransaction.getScheduleDay() + 1 == tempCal.get(Calendar.DAY_OF_WEEK)
						&& ((DateUtil.getDaysBetween(lastDayCreated, tempDate, false) >= 13)
								|| isNewTransaction)) {
					todayIsTheDay = true;
					lastDayCreated = (Date) tempDate.clone();
					if (isNewTransaction) {
						isNewTransaction = false;
					}
				}
				else if (scheduledTransaction.getFrequencyType().equals(ScheduleFrequency.SCHEDULE_FREQUENCY_EVERY_X_DAYS.toString())
						&& DateUtil.getDaysBetween(lastDayCreated, tempDate, false) >= scheduledTransaction.getScheduleDay()) {
					todayIsTheDay = true;
					lastDayCreated = (Date) tempDate.clone();
				}
				else if (scheduledTransaction.getFrequencyType().equals(ScheduleFrequency.SCHEDULE_FREQUENCY_EVERY_DAY.toString())) {
					todayIsTheDay = true;
				}
				else if (scheduledTransaction.getFrequencyType().equals(ScheduleFrequency.SCHEDULE_FREQUENCY_EVERY_WEEKDAY.toString())
						&& (tempCal.get(Calendar.DAY_OF_WEEK) < Calendar.SATURDAY)
						&& (tempCal.get(Calendar.DAY_OF_WEEK) > Calendar.SUNDAY)) {
					todayIsTheDay = true;
				}
				else if (scheduledTransaction.getFrequencyType().equals(ScheduleFrequency.SCHEDULE_FREQUENCY_MULTIPLE_WEEKS_EVERY_MONTH.toString())
						&& scheduledTransaction.getScheduleDay() + 1 == tempCal.get(Calendar.DAY_OF_WEEK)) {
					int week = scheduledTransaction.getScheduleWeek();
					int weekNumber = tempCal.get(Calendar.DAY_OF_WEEK_IN_MONTH) - 1;
					int weekMask = (int) Math.pow(2, weekNumber);
					if ((week & weekMask) != 0) {
						todayIsTheDay = true;
					}
				}
				else if (scheduledTransaction.getFrequencyType().equals(ScheduleFrequency.SCHEDULE_FREQUENCY_MULTIPLE_MONTHS_EVERY_YEAR.toString())
						&& scheduledTransaction.getScheduleDay() == tempCal.get(Calendar.DAY_OF_MONTH)) {
					int months = scheduledTransaction.getScheduleMonth();
					int monthMask = (int) Math.pow(2, tempCal.get(Calendar.MONTH));
					if ((months & monthMask) != 0) {
						todayIsTheDay = true;
					}
				}

				if (todayIsTheDay) {
					for (Transaction t : transactionsMapper.selectTransactions(user, tempDate, tempDate)) {
						if (DateUtil.isSameDay(t.getDate(), tempDate)
								&& t.getScheduledTransactionId() == scheduledTransaction.getId()) {
							todayIsTheDay = false;
							scheduledTransaction.setLastCreatedDate(tempDate);
						}
					}
				}

				if (todayIsTheDay) {

					scheduledTransaction.setLastCreatedDate(DateUtil.getEndOfDay(tempDate));

					final String decryptedMessage = CryptoUtil.decryptWrapper(scheduledTransaction.getMessage(), user);
					if (scheduledTransaction.getMessage() != null && StringUtils.isNotBlank(decryptedMessage)) {
						sb.append(FormatUtil.formatDate(tempDate, user)).append(": ").append(decryptedMessage).append("<br/>");
					}

					if (tempDate != null && scheduledTransaction.getDescription() != null) {
						final Transaction t = new Transaction();
						t.setDate(tempDate);
						t.setDescription(scheduledTransaction.getDescription());
						t.setNumber(scheduledTransaction.getNumber());
						t.setSplits(new ArrayList<Split>());
						for (Split split : scheduledTransaction.getSplits()) {
							split.setId(null);
							split.setTransactionId(null);
							t.getSplits().add(split);
						}
						t.setScheduledTransactionId(scheduledTransaction.getId());

						ConstraintsChecker.checkInsertTransaction(t, user, sources, crypto);
						int count = transactionsMapper.insertTransaction(user, t);
						if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
						for (Split split : t.getSplits()) {
							split.setTransactionId(t.getId());

							count = transactionsMapper.insertSplit(user, split);
							if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
						}

						ConstraintsChecker.checkUpdateScheduledTransaction(scheduledTransaction, user, sources, crypto);
						scheduledTransactionsMapper.updateScheduledTransaction(user, scheduledTransaction);

						thereWasAnUpate = true;
					}
				}

				tempDate = DateUtil.addDays(tempDate, 1);
			}
		}

		if (thereWasAnUpate) {
			DataUpdater.updateBalances(user, sources, transactionsMapper, crypto);
		}

		final String messages = sb.toString();
		return (thereWasAnUpate ? messages : null);
	}

	public static void turnOnEncryption(final User user, final Sources sources, final Entries entries,
			final Transactions transactionsMapper, final ScheduledTransactions scheduledTransactionsMapper,
			final Users users, final Crypto crypto) throws DatabaseException, CryptoException {
		if (user.isEncrypted()) throw new DatabaseException("This account is already encrypted");

		final String password = user.getPlaintextSecret();
		final SecretKey key = crypto.generateSecretKey();
		user.setEncryptionKey(crypto.encrypt(password, Crypto.encodeSecretKey(key)));

		int count = users.updateUserEncryptionKey(user);
		if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));

		for (Account a : sources.selectAccounts(user)) {
			a.setName(crypto.encrypt(key, a.getName()));
			a.setAccountType(crypto.encrypt(key, a.getAccountType()));
			a.setStartBalance(crypto.encrypt(key, a.getStartBalance()));
			a.setBalance(null);
			sources.updateAccount(user, a);
		}
		for (Category c : sources.selectCategories(user)) {
			c.setName(crypto.encrypt(key, c.getName()));
			sources.updateCategory(user, c);
		}
		for (Entry e : entries.selectEntries(user)) {
			e.setAmount(crypto.encrypt(key, e.getAmount()));
			entries.updateEntry(user, e);
		}
		for (Transaction t : transactionsMapper.selectTransactions(user)) {
			t.setDescription(crypto.encrypt(key, t.getDescription()));
			t.setNumber(crypto.encrypt(key, t.getNumber()));
			transactionsMapper.updateTransaction(user, t);
		}
		for (Split s : transactionsMapper.selectSplits(user)) {
			s.setAmount(crypto.encrypt(key, s.getAmount()));
			s.setMemo(crypto.encrypt(key, s.getMemo()));
			s.setFromBalance(null);
			s.setToBalance(null);
			transactionsMapper.updateSplit(user, s);
		}
		for (ScheduledTransaction st : scheduledTransactionsMapper.selectScheduledTransactions(user)) {
			st.setScheduleName(crypto.encrypt(key, st.getScheduleName()));
			st.setDescription(crypto.encrypt(key, st.getDescription()));
			st.setNumber(crypto.encrypt(key, st.getNumber()));
			st.setMessage(crypto.encrypt(key, st.getMessage()));
			scheduledTransactionsMapper.updateScheduledTransaction(user, st);
			for (Split s : st.getSplits()) {
				s.setAmount(crypto.encrypt(key, s.getAmount()));
				s.setMemo(crypto.encrypt(key, s.getMemo()));
				scheduledTransactionsMapper.updateScheduledSplit(user, s);
			}
		}
	}

	public static void turnOffEncryption(final User user, final Sources sources, final Entries entries,
			final Transactions transactionsMapper, final ScheduledTransactions scheduledTransactionsMapper,
			final Users users, final Crypto crypto) throws DatabaseException, CryptoException {
		if (!user.isEncrypted()) throw new DatabaseException("This account is not encrypted");

		final SecretKey key = user.getDecryptedSecretKey();
		user.setEncryptionKey(null);
		int count = users.updateUserEncryptionKey(user);
		if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));

		for (Account a : sources.selectAccounts(user)) {
			a.setName(Crypto.decrypt(key, a.getName()));
			a.setAccountType(Crypto.decrypt(key, a.getAccountType()));
			a.setStartBalance(Crypto.decrypt(key, a.getStartBalance()));
			a.setBalance(null);
			sources.updateAccount(user, a);
		}
		for (Category c : sources.selectCategories(user)) {
			c.setName(Crypto.decrypt(key, c.getName()));
			sources.updateCategory(user, c);
		}
		for (Entry e : entries.selectEntries(user)) {
			e.setAmount(Crypto.decrypt(key, e.getAmount()));
			entries.updateEntry(user, e);
		}
		for (Transaction t : transactionsMapper.selectTransactions(user)) {
			t.setDescription(Crypto.decrypt(key, t.getDescription()));
			t.setNumber(Crypto.decrypt(key, t.getNumber()));
			transactionsMapper.updateTransaction(user, t);
		}
		for (Split s : transactionsMapper.selectSplits(user)) {
			s.setAmount(Crypto.decrypt(key, s.getAmount()));
			s.setFromBalance(null);
			s.setToBalance(null);
			if (s.getMemo() != null) {
				s.setMemo(Crypto.decrypt(key, s.getMemo()));
			}
			transactionsMapper.updateSplit(user, s);
		}
		for (ScheduledTransaction st : scheduledTransactionsMapper.selectScheduledTransactions(user)) {
			try { st.setScheduleName(Crypto.decrypt(key, st.getScheduleName())); } catch (CryptoException e) {}
			st.setDescription(Crypto.decrypt(key, st.getDescription()));
			st.setNumber(Crypto.decrypt(key, st.getNumber()));
			try { st.setMessage(Crypto.decrypt(key, st.getMessage())); } catch (CryptoException e) {}
			scheduledTransactionsMapper.updateScheduledTransaction(user, st);
			for (Split s : st.getSplits()) {
				s.setAmount(Crypto.decrypt(key, s.getAmount()));
				s.setMemo(Crypto.decrypt(key, s.getMemo()));
				scheduledTransactionsMapper.updateScheduledSplit(user, s);
			}
		}
	}

	public static void upgradeEncryptionFrom1(final User user, final Sources sources, final Entries entries,
			final Transactions transactionsMapper, final ScheduledTransactions scheduledTransactionsMapper,
			final Users users, final Crypto crypto) throws DatabaseException, CryptoException {
		if (!user.isEncrypted()) {
			users.updateUserEncryptionVersion(user, 2);
			return;
		}

		final SecretKey key = user.getDecryptedSecretKey();

		for (Account account : sources.selectAccounts(user)) {
			final BigDecimal startBalance = new BigDecimal(account.getStartBalance());
			account.setStartBalance(crypto.encrypt(key, startBalance.toPlainString()));
			account.setBalance(null);
			sources.updateAccount(user, account);
		}
		for (Entry entry : entries.selectEntries(user)) {
			final BigDecimal amount = new BigDecimal(entry.getAmount());
			entry.setAmount(crypto.encrypt(key, amount.toPlainString()));
			entries.updateEntry(user, entry);
		}
		for (Split split : transactionsMapper.selectSplits(user)) {
			final BigDecimal amount = new BigDecimal(split.getAmount());
			split.setAmount(crypto.encrypt(key, amount.toPlainString()));
			split.setFromBalance(null);
			split.setToBalance(null);
			transactionsMapper.updateSplit(user, split);
		}
		for (ScheduledTransaction st : scheduledTransactionsMapper.selectScheduledTransactions(user)) {
			for (Split split : st.getSplits()) {
				final BigDecimal amount = new BigDecimal(split.getAmount());
				split.setAmount(crypto.encrypt(key, amount.toPlainString()));
				scheduledTransactionsMapper.updateScheduledSplit(user, split);
			}
		}

		users.updateUserEncryptionVersion(user, 2);

		updateBalances(user, sources, transactionsMapper, crypto);
	}
}