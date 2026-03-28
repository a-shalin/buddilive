package ca.digitalcave.buddi.live.model;

import ca.digitalcave.buddi.live.api.dto.request.SplitRequestDto;
import ca.digitalcave.buddi.live.api.dto.request.TransactionRequestDto;
import ca.digitalcave.buddi.live.util.FormatUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class Transaction {
	private Long id;
	private String uuid;
	private int userId;
	private String description;
	private String number;
	private Date date;
	private boolean deleted;
	private Long scheduledTransactionId;
	private Date created;
	private Date modified;
	private List<Split> splits;
	
	public Transaction() {
	}

	public static Transaction fromDto(final TransactionRequestDto dto) {
		final Transaction transaction = new Transaction();
		transaction.setId(dto.id());
		transaction.setUuid(dto.uuid() != null ? dto.uuid() : UUID.randomUUID().toString());
		transaction.setDescription(dto.description());
		transaction.setNumber(dto.number());
		transaction.setDate(dto.date() != null ? FormatUtil.parseDateInternal(dto.date()) : null);
		transaction.setDeleted(Boolean.TRUE.equals(dto.deleted()));
		if (dto.splits() != null) {
			final List<Split> splits = new ArrayList<>();
			for (final SplitRequestDto splitDto : dto.splits()) {
				splits.add(Split.fromDto(splitDto));
			}
			transaction.setSplits(splits);
		}
		return transaction;
	}
	
	public Long getId() {
		return id;
	}
	public void setId(final Long id) {
		this.id = id;
	}
	public String getUuid() {
		if (uuid == null){
			this.setUuid(UUID.randomUUID().toString());
		}
		return uuid;
	}
	public void setUuid(final String uuid) {
		this.uuid = uuid;
	}
	public int getUserId() {
		return userId;
	}
	public void setUserId(final int userId) {
		this.userId = userId;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(final String description) {
		this.description = description;
	}
	public Date getDate() {
		return date;
	}
	public void setDate(final Date date) {
		this.date = date;
	}
	public String getNumber() {
		return number;
	}
	public void setNumber(final String number) {
		this.number = number;
	}
	public boolean isDeleted() {
		return deleted;
	}
	public void setDeleted(final boolean deleted) {
		this.deleted = deleted;
	}
	public Long getScheduledTransactionId() {
		return scheduledTransactionId;
	}
	public void setScheduledTransactionId(final Long scheduledTransactionId) {
		this.scheduledTransactionId = scheduledTransactionId;
	}
	public List<Split> getSplits() {
		return splits;
	}
	public void setSplits(final List<Split> splits) {
		this.splits = splits;
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
	
	@Override
	public String toString() {
		return String.format("Transaction[id=%s, uuid=%s, description=%s, date=%s]", id, uuid, description, date);
	}
}
