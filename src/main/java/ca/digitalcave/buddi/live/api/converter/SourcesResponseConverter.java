package ca.digitalcave.buddi.live.api.converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import ca.digitalcave.buddi.live.api.dto.SourcesResponseDto;
import ca.digitalcave.buddi.live.api.dto.SourcesResponseDto.SourceItemDto;
import ca.digitalcave.buddi.live.model.Account;
import ca.digitalcave.buddi.live.model.AccountType;
import ca.digitalcave.buddi.live.model.Category;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

@Component
public class SourcesResponseConverter {

	public SourcesResponseDto convert(final User user,
			final List<AccountType> accountsByType,
			final List<Category> categories) throws CryptoException {
		final Map<String, AccountType> accountTypeMap = new TreeMap<>();
		for (final AccountType accountType : accountsByType) {
			final String key = (accountType.isDebit() ? "1" : "2") + CryptoUtil.decryptWrapper(accountType.getAccountType(), user);
			if (!accountTypeMap.containsKey(key)) {
				accountTypeMap.put(key, accountType);
			}
			else {
				accountTypeMap.get(key).getAccounts().addAll(accountType.getAccounts());
			}
		}

		final List<SourceItemDto> data = new ArrayList<>();
		data.add(new SourceItemDto("", "--- Accounts ---", "color: " + FormatUtil.HTML_GRAY + ";", null));

		final StringBuilder sb = new StringBuilder();
		for (final String key : accountTypeMap.keySet()) {
			final AccountType accountType = accountTypeMap.get(key);
			if (!accountType.isDeleted() || user.isShowDeleted()) {
				if (accountType.isDeleted()) sb.append(" text-decoration: line-through;");
				sb.append(" color: " + (accountType.isDebit() ? FormatUtil.HTML_GRAY : FormatUtil.HTML_DISABLED_RED) + ";");
				data.add(new SourceItemDto(
						"",
						CryptoUtil.decryptWrapper(accountType.getAccountType(), user),
						sb.toString(),
						null));
				sb.setLength(0);

				final List<Account> accounts = accountType.getAccounts() == null ? Collections.emptyList() : accountType.getAccounts();
				for (final Account account : accounts) {
					if (!account.isDeleted() || user.isShowDeleted()) {
						if (account.isDeleted()) sb.append(" text-decoration: line-through;");
						if (!account.isDebit()) sb.append(" color: " + FormatUtil.HTML_RED + ";");
						data.add(new SourceItemDto(
								account.getId(),
								StringUtils.repeat("\u00a0", 2) + CryptoUtil.decryptWrapper(account.getName(), user).replaceAll(" ", "\u00a0"),
								sb.toString(),
								account.getType()));
						sb.setLength(0);
					}
				}
			}
		}

		data.add(new SourceItemDto("", "--- Budget Categories ---", "color: " + FormatUtil.HTML_GRAY + ";", null));
		insertCategories(data, categories, user, 0);

		return new SourcesResponseDto(true, data);
	}

	private void insertCategories(final List<SourceItemDto> data,
			final List<Category> categories,
			final User user,
			final int depth) throws CryptoException {
		final StringBuilder sb = new StringBuilder();
		for (final Category category : categories) {
			if (!category.isDeleted() || user.isShowDeleted()) {
				if (category.isDeleted()) sb.append(" text-decoration: line-through;");
				if (!category.isIncome()) sb.append(" color: " + FormatUtil.HTML_RED + ";");
				data.add(new SourceItemDto(
						category.getId(),
						StringUtils.repeat("\u00a0", depth * 2) + CryptoUtil.decryptWrapper(category.getName(), user).replaceAll(" ", "\u00a0"),
						sb.toString(),
						category.getType()));
				sb.setLength(0);
				if (category.getChildren() != null) insertCategories(data, category.getChildren(), user, depth + 1);
			}
		}
	}
}
