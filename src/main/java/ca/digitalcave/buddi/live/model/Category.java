package ca.digitalcave.buddi.live.model;

import ca.digitalcave.buddi.live.api.dto.request.CategoriesRequestDto;
import ca.digitalcave.buddi.live.db.Entries;
import ca.digitalcave.buddi.live.model.CategoryPeriod.CategoryPeriods;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.moss.common.DateUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class Category extends Source {
	private String periodType;
	private Integer parent;
	
	//The following are used in Java, but are not directly mapped to the DB
	private List<Category> children;
	
	private Entry previousEntry;
	private Entry currentEntry;

	public Category() {}

	public static Category fromDto(final CategoriesRequestDto dto) {
		final Category category = new Category();
		category.setId(dto.id());
		category.setUuid(dto.uuid() != null ? dto.uuid() : UUID.randomUUID().toString());
		category.setName(dto.name());
		category.setDeleted(Boolean.TRUE.equals(dto.deleted()));
		category.setType(dto.type());
		category.setPeriodType(dto.periodType());
		category.setParent(dto.parent() != null && dto.parent() != 0 ? dto.parent() : null);
		return category;
	}
	
	public String getPeriodType() {
		return periodType;
	}
	public void setPeriodType(final String periodType) {
		this.periodType = periodType;
	}
	public Integer getParent() {
		return parent;
	}
	public void setParent(final Integer parent) {
		this.parent = parent;
	}
	public List<Category> getChildren() {
		return children;
	}
	public void setChildren(final List<Category> children) {
		this.children = children;
	}
	
	/**
	 * Creates a hierarchy of categories, with the correct parentage.  The resulting list will
	 * be all categories without parents, with children fields set accordingly.
	 * @param categories
	 * @return
	 */
	public static List<Category> getHierarchy(final List<Category> categories){
		final Map<Integer, Category> categoryMap = new HashMap<Integer, Category>();
		final List<Category> result = new ArrayList<Category>();
		final List<Category> remaining = new ArrayList<Category>();
		for (Category category : categories) {
			categoryMap.put(category.getId(), category);
			if (category.getParent() == null) result.add(category);
			else remaining.add(category);
		}
		
		while (remaining.size() > 0){
			final Category category = remaining.remove(0);
			final Category parent = categoryMap.get(category.getParent());
			if (parent != null){
				if (parent.getChildren() == null) parent.setChildren(new ArrayList<Category>());
				parent.getChildren().add(category);
			}
		}
		
		return result;
	}
	
	/**
	 * Returns the budgeted amount associated with this category, between the given dates.  If the start and end dates
	 * match the boundaries of a single period, then we just return the amount.  Otherwise, the amount is calculated
	 * based on the percentage of the category in the range.
	 * @param user
	 * @param entriesMapper
	 * @param startDate
	 * @param endDate
	 * @return
	 */
	public BigDecimal getAmount(final User user, final Entries entriesMapper, final Date startDate, final Date endDate) throws CryptoException {
		return getAmountRecursive(user, entriesMapper, startDate, endDate, null);
	}

	private BigDecimal getAmountRecursive(final User user, final Entries entriesMapper, final Date startDate, final Date endDate, Map<Date, Entry> entries) throws CryptoException {
		final CategoryPeriods categoryPeriod = CategoryPeriods.valueOf(getPeriodType());
		if (entries == null) entries = entriesMapper.selectEntries(user, getId());
		
		//If the start date and end date are in the same period, then our job is easy: find the entry, 
		// and return the amount * percent of how many days were used in the period. 
		if (categoryPeriod.getStartOfBudgetPeriod(startDate).equals(categoryPeriod.getStartOfBudgetPeriod(endDate))){
			if (entries.get(startDate) == null) return BigDecimal.ZERO;
			final BigDecimal totalAmount = CryptoUtil.decryptWrapperBigDecimal(entries.get(startDate).getAmount(), user, true);
			final double totalDays = categoryPeriod.getDaysInPeriod(startDate);
			final double daysBetween = DateUtil.getDaysBetween(startDate, endDate, true);
			final BigDecimal result = new BigDecimal(totalAmount.doubleValue() * (daysBetween / totalDays));
			result.setScale(2, RoundingMode.HALF_EVEN);
			return result;
		}
		//If the start date and end date are different, then we need to add each period separately, using the same
		// rules as the simple case above.
		else {
			Date periodStartDate = startDate;
			Date periodEndDate = categoryPeriod.getEndOfBudgetPeriod(startDate);
			BigDecimal total = BigDecimal.ZERO;
			while (categoryPeriod.getEndOfBudgetPeriod(periodEndDate).before(endDate)){
				total = total.add(getAmountRecursive(user, entriesMapper, periodStartDate, periodEndDate, entries));
				
				//The next start date is the first day in the next period
				periodStartDate = categoryPeriod.getBudgetPeriodOffset(periodStartDate, 1);
				//The next end date is the overall end date, or the end date in the next period, whichever is earlier
				periodEndDate = categoryPeriod.getEndOfBudgetPeriod(periodStartDate).before(endDate) ? categoryPeriod.getEndOfBudgetPeriod(periodStartDate) : endDate;
 			}
			total = total.add(getAmountRecursive(user, entriesMapper, periodStartDate, periodEndDate, entries));
			
			return total;
		}
	}
	
	public boolean isIncome(){
		return "I".equals(getType());
	}

	public Entry getCurrentEntry() {
		return currentEntry;
	}
	public void setCurrentEntry(final Entry currentEntry) {
		this.currentEntry = currentEntry;
	}
	public Entry getPreviousEntry() {
		return previousEntry;
	}
	public void setPreviousEntry(final Entry previousEntry) {
		this.previousEntry = previousEntry;
	}
}
