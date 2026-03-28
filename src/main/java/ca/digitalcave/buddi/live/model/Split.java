package ca.digitalcave.buddi.live.model;

import ca.digitalcave.buddi.live.api.dto.request.SplitRequestDto;
import ca.digitalcave.buddi.live.util.FormatUtil;

import java.util.Date;

public class Split {
	private Long id;
	private Long transactionId;
	private int userId;
	private String amount;
	private int fromSource;
	private int toSource;
	private String fromType;
	private String toType;
	private String memo;
	private Date created;
	private Date modified;
	private String fromBalance;
	private String toBalance;

	private String fromSourceName;
	private String toSourceName;
	
	public Split() {
	}

	public static Split fromDto(final SplitRequestDto dto) {
		final Split split = new Split();
		split.setId(dto.id());
		split.setTransactionId(dto.transactionId());
		split.setAmount(FormatUtil.parseCurrency(String.valueOf(dto.amount())).toPlainString());
		split.setFromSource(dto.fromId());
		split.setToSource(dto.toId());
		split.setMemo(dto.memo());
		return split;
	}
	
	public Long getId() {
		return id;
	}
	public void setId(final Long id) {
		this.id = id;
	}
	public Long getTransactionId() {
		return transactionId;
	}
	public void setTransactionId(final Long transactionId) {
		this.transactionId = transactionId;
	}
	public int getUserId() {
		return userId;
	}
	public void setUserId(final int userId) {
		this.userId = userId;
	}
	public String getAmount() {
		return amount;
	}
	public void setAmount(final String amount) {
		this.amount = amount;
	}
	public int getFromSource() {
		return fromSource;
	}
	public void setFromSource(final int fromSource) {
		this.fromSource = fromSource;
	}
	public int getToSource() {
		return toSource;
	}
	public void setToSource(final int toSource) {
		this.toSource = toSource;
	}
	public String getMemo() {
		return memo;
	}
	public void setMemo(final String memo) {
		this.memo = memo;
	}
	public String getFromBalance() {
		return fromBalance;
	}
	public void setFromBalance(final String fromBalance) {
		this.fromBalance = fromBalance;
	}
	public String getToBalance() {
		return toBalance;
	}
	public void setToBalance(final String toBalance) {
		this.toBalance = toBalance;
	}
	public String getFromType() {
		return fromType;
	}
	public void setFromType(final String fromType) {
		this.fromType = fromType;
	}
	public String getToType() {
		return toType;
	}
	public void setToType(final String toType) {
		this.toType = toType;
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
	
//	public boolean isInflow(){
//		if ("I".equals(getFromType())){
//			return this.getAmount().compareTo(BigDecimal.ZERO) >= 0;
//		}
//		if ("E".equals(getToType())){
//			return this.getAmount().compareTo(BigDecimal.ZERO) < 0;
//		}
//
//		//If neither sources are BudgetCategory, this is not an inflow.
//		return false;
//	}
	
	@Override
	public String toString() {
		return String.format("Split[id=%s, amount=%s, from=%s, to=%s]", id, amount, fromSource, toSource);
	}
	
	public String getFromSourceName() {
		return fromSourceName;
	}
	public String getToSourceName() {
		return toSourceName;
	}
	
	/**
	 * Should the amount appear on the debit or credit side in the display?
	 * @param source
	 * @return
	 */
	public boolean isDebit(final Source source){
		return (this.getFromSource() == source.getId() && "D".equals(source.getType())) 
				|| (this.getFromSource() == source.getId() && "C".equals(source.getType()));
	}
}
