package ca.digitalcave.buddi.live.api.converter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import ca.digitalcave.buddi.live.api.dto.CategoriesResponseDto;
import ca.digitalcave.buddi.live.api.dto.CategoriesResponseDto.CategoryNodeDto;
import ca.digitalcave.buddi.live.model.Category;
import ca.digitalcave.buddi.live.model.CategoryPeriod;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.Transaction;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

@Component
public class CategoriesResponseConverter {

	public CategoriesResponseDto convert(final User user,
			final CategoryPeriod categoryPeriod,
			final List<Category> categories,
			final List<Transaction> txns) throws CryptoException {
		final List<CategoryNodeDto> children = new ArrayList<>();
		for (final Category category : categories) {
			final CategoryNodeDto node = convertCategoryNode(category, categoryPeriod, txns, user);
			if (node != null) {
				children.add(node);
			}
		}

		return new CategoriesResponseDto(
				true,
				FormatUtil.formatDate(categoryPeriod.getCurrentPeriodStartDate(), user) + " - "
						+ FormatUtil.formatDate(categoryPeriod.getCurrentPeriodEndDate(), user),
				FormatUtil.formatDateInternal(categoryPeriod.getCurrentPeriodStartDate()),
				FormatUtil.formatDate(categoryPeriod.getPreviousPeriodStartDate(), user) + " - "
						+ FormatUtil.formatDate(categoryPeriod.getPreviousPeriodEndDate(), user),
				children);
	}

	public CategoryNodeDto convertCategoryNode(final Category category,
			final CategoryPeriod categoryPeriod,
			final List<Transaction> txns,
			final User user) throws CryptoException {
		if (category.isDeleted() && !user.isShowDeleted()) {
			return null;
		}

		BigDecimal actualAmount = BigDecimal.ZERO;
		for (final Transaction transaction : txns) {
			for (final Split split : transaction.getSplits()) {
				if (split.getFromSource() == category.getId() || split.getToSource() == category.getId()) {
					actualAmount = actualAmount.add(CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, true));
				}
			}
		}

		final StringBuilder sb = new StringBuilder();
		if (category.isDeleted()) {
			sb.append(" text-decoration: line-through;");
		}
		if (!category.isIncome()) {
			sb.append(" color: ").append(FormatUtil.HTML_RED).append(";");
		}
		final String nameStyle = sb.toString();
		sb.setLength(0);

		final BigDecimal currentAmount = CryptoUtil.decryptWrapperBigDecimal(category.getCurrentEntry().getAmount(), user, true);
		final String currentStyle = (currentAmount.compareTo(BigDecimal.ZERO) == 0)
				? FormatUtil.formatGray()
				: (FormatUtil.isRed(category, currentAmount) ? FormatUtil.formatRed() : "");

		final BigDecimal previousAmount = CryptoUtil.decryptWrapperBigDecimal(category.getPreviousEntry().getAmount(), user, true);
		final String previousStyle = (previousAmount.compareTo(BigDecimal.ZERO) == 0)
				? FormatUtil.formatGray()
				: (FormatUtil.isRed(category, previousAmount) ? FormatUtil.formatRed() : "");

		final String actualStyle = (actualAmount.compareTo(BigDecimal.ZERO) == 0)
				? FormatUtil.formatGray()
				: (FormatUtil.isRed(category, actualAmount) ? FormatUtil.formatRed() : "");

		final BigDecimal differenceAmount = actualAmount.subtract(currentAmount != null ? currentAmount : BigDecimal.ZERO);
		final BigDecimal formattedDifferenceAmount = category.isIncome() ? differenceAmount : differenceAmount.negate();
		final String differenceStyle = (differenceAmount.compareTo(BigDecimal.ZERO) == 0)
				? FormatUtil.formatGray()
				: (FormatUtil.isRed(formattedDifferenceAmount) ? FormatUtil.formatRed() : "");

		final List<CategoryNodeDto> childNodes = new ArrayList<>();
		final List<Category> children = category.getChildren();
		if (children != null) {
			children.sort((o1, o2) -> {
				if (o1 == null || o2 == null) {
					return 0;
				}
				try {
					return CryptoUtil.decryptWrapper(o1.getName(), user)
							.compareTo(CryptoUtil.decryptWrapper(o2.getName(), user));
				}
				catch (final CryptoException e) {
					return 0;
				}
			});
			for (final Category child : children) {
				final CategoryNodeDto node = convertCategoryNode(child, categoryPeriod, txns, user);
				if (node != null) {
					childNodes.add(node);
				}
			}
		}

		final boolean hasChildren = !childNodes.isEmpty();
		return new CategoryNodeDto(
				category.getId(),
				"img/folder-open-table.png",
				FormatUtil.formatDateInternal(categoryPeriod.getCurrentPeriodStartDate()),
				categoryPeriod.getPeriodType().toString(),
				category.getType(),
				CryptoUtil.decryptWrapper(category.getName(), user),
				nameStyle,
				FormatUtil.formatCurrency(currentAmount, user),
				currentStyle,
				FormatUtil.formatCurrency(previousAmount, user),
				previousStyle,
				FormatUtil.formatCurrency(actualAmount, user),
				actualStyle,
				FormatUtil.formatCurrency(formattedDifferenceAmount, user),
				differenceStyle,
				category.getParent(),
				category.isDeleted(),
				hasChildren ? true : null,
				hasChildren ? null : true,
				hasChildren ? childNodes : null);
	}
}
