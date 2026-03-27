package ca.digitalcave.buddi.live.model;

import ca.digitalcave.buddi.live.api.dto.request.AccountRequestDto;
import ca.digitalcave.buddi.live.util.FormatUtil;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

public class Account extends Source {
	private String accountType;
	private String startBalance;
	private Date startDate;

	private String balance;

	public Account() {}

	public static Account fromDto(final AccountRequestDto dto) {
		final Account account = new Account();
		account.setId(dto.id());
		account.setUuid(dto.uuid() != null ? dto.uuid() : UUID.randomUUID().toString());
		account.setName(dto.name());
		account.setDeleted(Boolean.TRUE.equals(dto.deleted()));
		account.setType(dto.type());
		account.setAccountType(dto.accountType());
		final BigDecimal startBalance = FormatUtil.parseCurrency(dto.startBalance());
		if (startBalance != null) account.setStartBalance(startBalance.toPlainString());
		account.setStartDate(FormatUtil.parseDateInternal(dto.startDate() != null ? dto.startDate() : "1900-01-01"));
		return account;
	}

	public String getAccountType() {
		return accountType;
	}
	public void setAccountType(String accountType) {
		this.accountType = accountType;
	}
	public String getStartBalance() {
		return startBalance;
	}
	public void setStartBalance(String startBalance) {
		this.startBalance = startBalance;
	}
	public Date getStartDate() {
		return startDate;
	}
	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}
	
	public boolean isDebit(){
		return "D".equals(getType());
	}
	public String getBalance() {
		return balance;
	}
	public void setBalance(String balance) {
		this.balance = balance;
	}
}
