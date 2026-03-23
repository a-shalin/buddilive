package ca.digitalcave.buddi.live.controller;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ca.digitalcave.buddi.live.db.Sources;
import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.db.util.ConstraintsChecker;
import ca.digitalcave.buddi.live.db.util.DataUpdater;
import ca.digitalcave.buddi.live.db.util.DatabaseException;
import ca.digitalcave.buddi.live.model.Account;
import ca.digitalcave.buddi.live.model.AccountType;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

@RestController
@RequestMapping("/data/accounts")
public class AccountsController {

	@Autowired
	private Sources sources;

	@Autowired
	private Transactions transactions;

	@Autowired
	private Crypto crypto;

	@GetMapping
	public String get(@AuthenticationPrincipal User user) {
		try {
			final List<AccountType> accountsByType = sources.selectAccountTypes(user);
			final Map<String, AccountType> accountTypeMap = new TreeMap<>();
			for (AccountType accountType : accountsByType) {
				final String key = (accountType.isDebit() ? "1" : "2") + CryptoUtil.decryptWrapper(accountType.getAccountType(), user);
				if (!accountTypeMap.containsKey(key)) {
					accountTypeMap.put(key, accountType);
				}
				else {
					accountTypeMap.get(key).getAccounts().addAll(accountType.getAccounts());
				}
			}

			final JSONArray data = new JSONArray();
			final StringBuilder sb = new StringBuilder();
			BigDecimal netWorth = BigDecimal.ZERO;
			for (String key : accountTypeMap.keySet()) {
				final JSONObject type = new JSONObject();
				final AccountType t = accountTypeMap.get(key);
				type.put("name", CryptoUtil.decryptWrapper(t.getAccountType(), user));
				type.put("expanded", true);
				type.put("debit", t.isDebit());
				if (!t.isDebit()) {
					sb.append(" color: " + FormatUtil.HTML_RED + ";");
				}
				sb.append(FormatUtil.formatBold());
				type.put("style", sb.toString());
				sb.setLength(0);
				type.put("nodeType", "type");
				type.put("icon", "img/folder-open-table.png");
				final List<Account> accounts = t.getAccounts();
				Collections.sort(accounts, new Comparator<Account>() {
					@Override
					public int compare(Account o1, Account o2) {
						if (o1 == null || o2 == null) return 0;
						try {
							return CryptoUtil.decryptWrapper(o1.getName(), user).compareTo(CryptoUtil.decryptWrapper(o2.getName(), user));
						}
						catch (CryptoException e) {
							return 0;
						}
					}
				});
				BigDecimal total = BigDecimal.ZERO;
				for (Account a : accounts) {
					if (!a.isDeleted() || user.isShowDeleted()) {
						final JSONObject account = new JSONObject();
						account.put("id", a.getId());
						account.put("name", CryptoUtil.decryptWrapper(a.getName(), user));
						final BigDecimal balance = CryptoUtil.decryptWrapperBigDecimal(a.getBalance(), user, true);
						account.put("balance", FormatUtil.formatCurrency(a.isDebit() ? balance : balance.negate(), user));
						account.put("balanceStyle", (FormatUtil.isRed(a, balance) ? FormatUtil.formatRed() : ""));
						account.put("type", a.getType());
						account.put("accountType", CryptoUtil.decryptWrapper(a.getAccountType(), user));
						final BigDecimal startBalance = CryptoUtil.decryptWrapperBigDecimal(a.getStartBalance(), user, true);
						account.put("startBalance", startBalance);
						account.put("debit", a.isDebit());
						account.put("deleted", a.isDeleted());
						if (a.isDeleted()) sb.append(" text-decoration: line-through;");
						if (!a.isDebit()) sb.append(" color: " + FormatUtil.HTML_RED + ";");
						account.put("style", sb.toString());
						sb.setLength(0);
						account.put("leaf", true);
						account.put("nodeType", "account");
						account.put("icon", "img/table-money.png");
						type.append("children", account);
						total = total.add(a.isDebit() ? balance : balance.negate());
					}
				}
				type.put("balance", FormatUtil.formatCurrency(total, user));
				type.put("balanceStyle", FormatUtil.formatBold() + (FormatUtil.isRed(t.isDebit() ? total : total.negate()) ? FormatUtil.formatRed() : ""));
				netWorth = netWorth.add(t.isDebit() ? total : total.negate());
				if (type.has("children")) {
					data.put(type);
				}
			}

			final JSONObject netWorthNode = new JSONObject();
			netWorthNode.put("name", LocaleUtil.getTranslation(user).getString("NET_WORTH"));
			netWorthNode.put("style", FormatUtil.formatBold());
			netWorthNode.put("leaf", true);
			netWorthNode.put("icon", "img/table-sum.png");
			netWorthNode.put("balance", FormatUtil.formatCurrency(netWorth, user));
			netWorthNode.put("balanceStyle", FormatUtil.formatBold() + (FormatUtil.isRed(netWorth) ? FormatUtil.formatRed() : ""));
			data.put(netWorthNode);

			final JSONObject result = new JSONObject();
			result.put("children", data);
			result.put("success", true);
			return result.toString();
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	@PostMapping
	@Transactional
	public String post(@AuthenticationPrincipal User user, @RequestBody String body) {
		try {
			final JSONObject request = new JSONObject(body);
			final String action = request.optString("action");
			final Account account = new Account(request);

			if ("insert".equals(action)) {
				ConstraintsChecker.checkInsertAccount(account, user, sources, crypto);
				final int count = sources.insertAccount(user, account);
				if (count != 1) throw new DatabaseException(String.format("Insert failed; expected 1 row, returned %s", count));
			}
			else if ("delete".equals(action) || "undelete".equals(action)) {
				if (sources.selectSourceAssociatedCount(user, account) == 0) {
					final int count = sources.deleteSource(user, account);
					if (count != 1) throw new DatabaseException(String.format("Delete failed; expected 1 row, returned %s", count));
				}
				else {
					account.setDeleted("delete".equals(action));
					final int count = sources.updateSourceDeleted(user, account);
					if (count != 1) throw new DatabaseException(String.format("Delete / undelete failed; expected 1 row, returned %s", count));
				}
			}
			else if ("update".equals(action)) {
				ConstraintsChecker.checkUpdateAccount(account, user, sources, crypto);
				final int count = sources.updateAccount(user, account);
				if (count != 1) throw new DatabaseException(String.format("Update failed; expected 1 row, returned %s", count));
			}
			else {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, LocaleUtil.getTranslation(user).getString("ACTION_PARAMETER_MUST_BE_SPECIFIED"));
			}

			DataUpdater.updateBalances(user, sources, transactions, crypto);

			final JSONObject result = new JSONObject();
			result.put("success", true);
			return result.toString();
		}
		catch (DatabaseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}
}