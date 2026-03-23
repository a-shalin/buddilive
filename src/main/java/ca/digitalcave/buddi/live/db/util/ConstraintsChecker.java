package ca.digitalcave.buddi.live.db.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import ca.digitalcave.buddi.live.db.Entries;
import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Users;
import ca.digitalcave.buddi.live.model.Account;
import ca.digitalcave.buddi.live.model.Category;
import ca.digitalcave.buddi.live.model.Entry;
import ca.digitalcave.buddi.live.model.ScheduledTransaction;
import ca.digitalcave.buddi.live.model.Source;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.Transaction;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.moss.crypto.Base64;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

public class ConstraintsChecker {

	public static void checkInsertCategory(Category category, User user, Sources sources, Crypto crypto) throws DatabaseException, CryptoException {
		if (category.getParent() != null) {
			final Source parent = sources.selectSource(user, category.getParent());
			if (parent.isAccount()) {
				throw new DatabaseException("The parent of a category cannot be an account");
			}
			if (parent.getUserId() != user.getId()) {
				throw new DatabaseException("The userId of a parent category must match the userId of the child category");
			}
			final Map<Integer, Category> categories = sources.selectCategoriesMap(user);
			Category p = category;
			while (p.getParent() != null) {
				p = categories.get(p.getParent());
				if (p.getId() == category.getId()) throw new DatabaseException("Loop detected in category parentage");
				if (!p.getType().equals(category.getType())) throw new DatabaseException("The type of a parent must match the type of the child");
				if (!p.getPeriodType().equals(category.getPeriodType())) throw new DatabaseException("The period of a parent must match the period of the child");
			}
		}

		if (user.isEncrypted() && !isEncryptedValue(category.getName())) {
			category.setName(crypto.encrypt(user.getDecryptedSecretKey(), category.getName()));
		}
	}

	public static void checkUpdateCategory(Category category, User user, Sources sources, Crypto crypto) throws DatabaseException, CryptoException {
		if (category.getId() == null) throw new DatabaseException("The id must be set to perform an update");
		checkInsertCategory(category, user, sources, crypto);
	}

	public static void checkInsertAccount(Account account, User user, Sources sources, Crypto crypto) throws DatabaseException, CryptoException {
		if (account.isAccount()) {
			final List<Account> accounts = sources.selectAccounts(user, account.getAccountType());
			for (Account a : accounts) {
				if (!a.getType().equals(account.getType())) {
					if ("D".equals(account.getType())) throw new DatabaseException(String.format("There is already an account type '%s' for a credit account.  Please change the account type, or set the type to credit.", account.getAccountType()));
					else throw new DatabaseException(String.format("There is already an account type '%s' for a debit account.  Please change the account type, or set the type to debit.", account.getAccountType()));
				}
			}
		}
		if (user.isEncrypted() && !isEncryptedValue(account.getName())) {
			account.setName(crypto.encrypt(user.getDecryptedSecretKey(), account.getName()));
		}
		if (user.isEncrypted() && !isEncryptedValue(account.getAccountType())) {
			account.setAccountType(crypto.encrypt(user.getDecryptedSecretKey(), account.getAccountType()));
		}
		if (user.isEncrypted() && !isEncryptedValue(account.getBalance())) {
			account.setBalance(crypto.encrypt(user.getDecryptedSecretKey(), account.getBalance()));
		}
		if (user.isEncrypted() && !isEncryptedValue(account.getStartBalance())) {
			account.setStartBalance(crypto.encrypt(user.getDecryptedSecretKey(), account.getStartBalance()));
		}
	}

	public static void checkUpdateAccount(Account account, User user, Sources sources, Crypto crypto) throws DatabaseException, CryptoException {
		if (account.getId() == null) throw new DatabaseException("The id must be set to perform an update");
		checkInsertAccount(account, user, sources, crypto);
	}

	public static void checkInsertSplit(Split split, User user, Sources sources, Crypto crypto) throws DatabaseException, CryptoException {
		final Source fromSource = sources.selectSource(user, split.getFromSource());
		final Source toSource = sources.selectSource(user, split.getToSource());
		if (!fromSource.isAccount() && !toSource.isAccount()) throw new DatabaseException("From and To cannot both be categories");
		if (fromSource.getId() == toSource.getId()) throw new DatabaseException("From and To cannot be the same");

		if (user.isEncrypted() && !isEncryptedValue(split.getMemo())) {
			split.setMemo(crypto.encrypt(user.getDecryptedSecretKey(), split.getMemo()));
		}
		if (user.isEncrypted() && !isEncryptedValue(split.getAmount())) {
			split.setAmount(crypto.encrypt(user.getDecryptedSecretKey(), split.getAmount()));
		}
		if (user.isEncrypted() && !isEncryptedValue(split.getFromBalance())) {
			split.setFromBalance(crypto.encrypt(user.getDecryptedSecretKey(), split.getFromBalance()));
		}
		if (user.isEncrypted() && !isEncryptedValue(split.getToBalance())) {
			split.setToBalance(crypto.encrypt(user.getDecryptedSecretKey(), split.getToBalance()));
		}

		if (CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true).compareTo(BigDecimal.ZERO) == 0) throw new DatabaseException("Splits cannot have amounts equal to zero.");
	}

	public static void checkUpdateSplit(Split split, User user, Sources sources, Crypto crypto) throws DatabaseException, CryptoException {
		if (split.getId() == null) throw new DatabaseException("The id must be set to perform an update");
		checkInsertSplit(split, user, sources, crypto);
	}

	public static void checkInsertTransaction(Transaction transaction, User user, Sources sources, Crypto crypto) throws DatabaseException, CryptoException {
		if (transaction.getSplits() == null || transaction.getSplits().size() == 0) throw new DatabaseException("A transaction must contain at least one split.");
		if (transaction.getDate() == null) throw new DatabaseException("The transaction date must be set");
		for (Split split : transaction.getSplits()) {
			checkInsertSplit(split, user, sources, crypto);
		}

		if (user.isEncrypted() && !isEncryptedValue(transaction.getDescription())) {
			transaction.setDescription(crypto.encrypt(user.getDecryptedSecretKey(), transaction.getDescription()));
		}
		if (user.isEncrypted() && !isEncryptedValue(transaction.getNumber())) {
			transaction.setNumber(crypto.encrypt(user.getDecryptedSecretKey(), transaction.getNumber()));
		}
	}

	public static void checkUpdateTransaction(Transaction transaction, User user, Sources sources, Crypto crypto) throws DatabaseException, CryptoException {
		if (transaction.getId() == null) throw new DatabaseException("The id must be set to perform an update");
		checkInsertTransaction(transaction, user, sources, crypto);
	}

	public static void checkInsertScheduledTransaction(ScheduledTransaction scheduledTransaction, User user, Sources sources, Crypto crypto) throws DatabaseException, CryptoException {
		if (scheduledTransaction.getSplits() == null || scheduledTransaction.getSplits().size() == 0) throw new DatabaseException("A transaction must contain at least one split.");
		for (Split split : scheduledTransaction.getSplits()) {
			if (CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true).compareTo(BigDecimal.ZERO) == 0) throw new DatabaseException("Splits cannot have amounts equal to zero.");

			final Source fromSource = sources.selectSource(user, split.getFromSource());
			final Source toSource = sources.selectSource(user, split.getToSource());
			if (!fromSource.isAccount() && !toSource.isAccount()) throw new DatabaseException("From and To cannot both be categories");
			if (fromSource.getId() == toSource.getId()) throw new DatabaseException("From and To cannot be the same");

			if (user.isEncrypted() && !isEncryptedValue(split.getMemo())) {
				split.setMemo(crypto.encrypt(user.getDecryptedSecretKey(), split.getMemo()));
			}
			if (user.isEncrypted() && !isEncryptedValue(split.getAmount())) {
				split.setAmount(crypto.encrypt(user.getDecryptedSecretKey(), split.getAmount()));
			}
		}

		if (user.isEncrypted() && !isEncryptedValue(scheduledTransaction.getScheduleName())) {
			scheduledTransaction.setScheduleName(crypto.encrypt(user.getDecryptedSecretKey(), scheduledTransaction.getScheduleName()));
		}
		if (user.isEncrypted() && !isEncryptedValue(scheduledTransaction.getDescription())) {
			scheduledTransaction.setDescription(crypto.encrypt(user.getDecryptedSecretKey(), scheduledTransaction.getDescription()));
		}
		if (user.isEncrypted() && !isEncryptedValue(scheduledTransaction.getNumber())) {
			scheduledTransaction.setNumber(crypto.encrypt(user.getDecryptedSecretKey(), scheduledTransaction.getNumber()));
		}
		if (user.isEncrypted() && !isEncryptedValue(scheduledTransaction.getScheduleName())) {
			scheduledTransaction.setScheduleName(crypto.encrypt(user.getDecryptedSecretKey(), scheduledTransaction.getScheduleName()));
		}
		if (user.isEncrypted() && !isEncryptedValue(scheduledTransaction.getMessage())) {
			scheduledTransaction.setMessage(crypto.encrypt(user.getDecryptedSecretKey(), scheduledTransaction.getMessage()));
		}
	}

	public static void checkUpdateScheduledTransaction(ScheduledTransaction scheduledTransaction, User user, Sources sources, Crypto crypto) throws DatabaseException, CryptoException {
		if (scheduledTransaction.getId() == null) throw new DatabaseException("The id must be set to perform an update");
		checkInsertScheduledTransaction(scheduledTransaction, user, sources, crypto);
	}

	public static void checkInsertEntry(Entry entry, User user, Sources sources, Crypto crypto) throws DatabaseException, CryptoException {
		if (entry.getAmount() == null) entry.setAmount(BigDecimal.ZERO.toPlainString());
		if (entry.getCategoryId() == 0) throw new DatabaseException("The category id must be set");
		if (entry.getDate() == null) throw new DatabaseException("The date must be set");

		if (sources.selectCategory(user, entry.getCategoryId()) == null) throw new DatabaseException("The specified category is not valid");

		if (user.isEncrypted() && !isEncryptedValue(entry.getAmount())) {
			entry.setAmount(crypto.encrypt(user.getDecryptedSecretKey(), entry.getAmount()));
		}
	}

	public static void checkUpdateEntry(Entry entry, User user, Sources sources, Entries entries, Crypto crypto) throws DatabaseException, CryptoException {
		if (entries.selectEntry(user, entry) == null) throw new DatabaseException("Could not find an entry to update");
		checkInsertEntry(entry, user, sources, crypto);
	}

	public static void checkInsertUser(User user, Users users) throws DatabaseException {
		if (users.selectUser(user.getIdentifier()) != null) throw new DatabaseException("The user name already exists");
	}

	public static void checkUpdateUserPreferences(User user) throws DatabaseException {
		if (!user.isPremium()) user.setShowCleared(false);
		if (!user.isPremium()) user.setShowReconciled(false);
	}

	private static boolean isEncryptedValue(String value) {
		if (StringUtils.isBlank(value)) return false;
		final String[] split = value.split(":");
		if (split.length != 3 && split.length != 4 && split.length != 5) {
			return false;
		}

		try {
			int algorithmId = Integer.parseInt(split[0]);
			if (algorithmId < 0 || algorithmId > 3) {
				return false;
			}

			if (Base64.decode(split[2]).length <= 4) {
				return false;
			}

			if (Base64.decode(split[split.length - 1]).length <= 4) {
				return false;
			}
		}
		catch (Throwable e) {
			return false;
		}

		return true;
	}
}