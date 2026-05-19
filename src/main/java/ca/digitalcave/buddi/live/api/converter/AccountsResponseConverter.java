package ca.digitalcave.buddi.live.api.converter;

import ca.digitalcave.buddi.live.api.dto.AccountsResponseDto;
import ca.digitalcave.buddi.live.api.dto.AccountsResponseDto.AccountNodeDto;
import ca.digitalcave.buddi.live.api.dto.AccountsResponseDto.AccountTypeNodeDto;
import ca.digitalcave.buddi.live.api.dto.AccountsResponseDto.AccountsNodeDto;
import ca.digitalcave.buddi.live.api.dto.AccountsResponseDto.NetWorthNodeDto;
import ca.digitalcave.buddi.live.model.Account;
import ca.digitalcave.buddi.live.model.AccountType;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class AccountsResponseConverter {

	public AccountsResponseDto convert(final User user, final List<AccountType> accountsByType) throws CryptoException {
		final Map<String, AccountType> accountTypeMap = new TreeMap<>();
		for (final AccountType accountType : accountsByType) {
			final String key = (accountType.isDebit() ? "1" : "2")
					+ CryptoUtil.decryptWrapper(accountType.getAccountType(), user);
			if (!accountTypeMap.containsKey(key)) {
				accountTypeMap.put(key, accountType);
			}
			else {
				accountTypeMap.get(key).getAccounts().addAll(accountType.getAccounts());
			}
		}

		final List<AccountsNodeDto> data = new ArrayList<>();
		BigDecimal netWorth = BigDecimal.ZERO;
		for (final String key : accountTypeMap.keySet()) {
			final AccountType accountType = accountTypeMap.get(key);
			final String typeName = CryptoUtil.decryptWrapper(accountType.getAccountType(), user);
			final String typeStyle = (accountType.isDebit() ? "" : " color: " + FormatUtil.HTML_RED + ";")
					+ FormatUtil.formatBold();

			final List<Account> accounts = new ArrayList<>(accountType.getAccounts());
			accounts.sort((o1, o2) -> {
                if (o1 == null || o2 == null) return 0;
                try {
                    return CryptoUtil.decryptWrapper(o1.getName(), user)
                            .compareTo(CryptoUtil.decryptWrapper(o2.getName(), user));
                } catch (final CryptoException e) {
                    return 0;
                }
            });

			final List<AccountNodeDto> children = new ArrayList<>();
			BigDecimal total = BigDecimal.ZERO;
			for (final Account account : accounts) {
				if (!account.isDeleted() || user.isShowDeleted()) {
					final BigDecimal balance = CryptoUtil.decryptWrapperBigDecimal(account.getBalance(), user, true);
					final BigDecimal startBalance = CryptoUtil.decryptWrapperBigDecimal(account.getStartBalance(), user, true);
					final String style = (account.isDeleted() ? " text-decoration: line-through;" : "")
							+ (!account.isDebit() ? " color: " + FormatUtil.HTML_RED + ";" : "");
					children.add(new AccountNodeDto(
							account.getId(),
							CryptoUtil.decryptWrapper(account.getName(), user),
							FormatUtil.formatCurrency(account.isDebit() ? balance : balance.negate(), user),
							balance,
							FormatUtil.isRed(account, balance) ? FormatUtil.formatRed() : "",
							account.getType(),
							CryptoUtil.decryptWrapper(account.getAccountType(), user),
							startBalance,
							account.isDebit(),
							account.isDeleted(),
							style,
							true,
							"account",
							"img/table-money.png"));
					total = total.add(account.isDebit() ? balance : balance.negate());
				}
			}

			final String balanceStyle = FormatUtil.formatBold()
					+ (FormatUtil.isRed(accountType.isDebit() ? total : total.negate()) ? FormatUtil.formatRed() : "");
			netWorth = netWorth.add(accountType.isDebit() ? total : total.negate());
			if (!children.isEmpty()) {
				data.add(new AccountTypeNodeDto(
						typeName,
						true,
						accountType.isDebit(),
						typeStyle,
						"type",
						"img/folder-open-table.png",
						FormatUtil.formatCurrency(total, user),
						total,
						balanceStyle,
						children));
			}
		}

		data.add(new NetWorthNodeDto(
				LocaleUtil.getTranslation(user).getString("NET_WORTH"),
				FormatUtil.formatBold(),
				true,
				"img/table-sum.png",
				FormatUtil.formatCurrency(netWorth, user),
				netWorth,
				FormatUtil.formatBold() + (FormatUtil.isRed(netWorth) ? FormatUtil.formatRed() : "")));

		return new AccountsResponseDto(true, data);
	}
}
