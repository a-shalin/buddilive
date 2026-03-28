package ca.digitalcave.buddi.live.model;

import ca.digitalcave.buddi.live.api.dto.request.CategoriesRequestDto;
import ca.digitalcave.buddi.live.util.FormatUtil;

import java.util.Date;

public class Entry {
	private Long id;
	private int categoryId;
	private String amount;
	private Date date;
	private Date created;
	private Date modified;
	private CategoryPeriod period;
	
	public Entry() {}

	public static Entry fromDto(final CategoriesRequestDto dto) {
		final Entry entry = new Entry();
		entry.setCategoryId(dto.categoryId());
		entry.setAmount(FormatUtil.parseCurrency(dto.amount()).toPlainString());
		entry.setDate(FormatUtil.parseDateInternal(dto.date()));
		return entry;
	}
	
	public Long getId() {
		return id;
	}
	public void setId(final Long id) {
		this.id = id;
	}
	public int getCategoryId() {
		return categoryId;
	}
	public void setCategoryId(final int categoryId) {
		this.categoryId = categoryId;
	}
	public String getAmount() {
		return amount;
	}
	public void setAmount(final String amount) {
		this.amount = amount;
	}
	public Date getDate() {
		return date;
	}
	public void setDate(final Date date) {
		this.date = date;
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
	public CategoryPeriod getPeriod() {
		return period;
	}
	public void setPeriod(final CategoryPeriod period) {
		this.period = period;
	}
	
	@Override
	public String toString() {
		return String.format("Entry[id=%s, categoryId=%s, amount=%s, date=%s]", id, categoryId, amount, date);
	}
}
