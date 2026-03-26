package ca.digitalcave.buddi.live.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record AccountsResponseDto(boolean success, List<AccountsNodeDto> children) {

	public interface AccountsNodeDto {
	}

	public record AccountTypeNodeDto(
			String name,
			boolean expanded,
			boolean debit,
			String style,
			String nodeType,
			String icon,
			String balance,
			String balanceStyle,
			List<AccountNodeDto> children) implements AccountsNodeDto {
	}

	public record AccountNodeDto(
			long id,
			String name,
			String balance,
			String balanceStyle,
			String type,
			String accountType,
			BigDecimal startBalance,
			boolean debit,
			boolean deleted,
			String style,
			boolean leaf,
			String nodeType,
			String icon) implements AccountsNodeDto {
	}

	public record NetWorthNodeDto(
			String name,
			String style,
			boolean leaf,
			String icon,
			String balance,
			String balanceStyle) implements AccountsNodeDto {
	}
}
