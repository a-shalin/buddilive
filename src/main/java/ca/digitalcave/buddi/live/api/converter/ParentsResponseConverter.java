package ca.digitalcave.buddi.live.api.converter;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import ca.digitalcave.buddi.live.api.dto.ParentsResponseDto;
import ca.digitalcave.buddi.live.api.dto.ParentsResponseDto.ParentItemDto;
import ca.digitalcave.buddi.live.model.Category;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.buddi.live.util.LocaleUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

@Component
public class ParentsResponseConverter {

	public ParentsResponseDto convert(final User user,
			final List<Category> categories,
			final Category exclude) throws CryptoException {
		final List<ParentItemDto> data = new ArrayList<>();
		data.add(new ParentItemDto("", LocaleUtil.getTranslation(user).getString("TOP_LEVEL"), null, null, null, null));
		addCategories(data, categories, exclude, user, 0);
		return new ParentsResponseDto(true, data);
	}

	private void addCategories(final List<ParentItemDto> data,
			final List<Category> categories,
			final Category exclude,
			final User user,
			final int depth) throws CryptoException {
		final StringBuilder sb = new StringBuilder();
		for (final Category category : categories) {
			if (exclude != null
					&& (category.getId().equals(exclude.getId())
					|| !category.getType().equals(exclude.getType())
					|| !category.getPeriodType().equals(exclude.getPeriodType()))) {
				continue;
			}
			if (category.isDeleted()) {
				sb.append(" text-decoration: line-through;");
			}
			if (!category.isIncome()) {
				sb.append(" color: ").append(FormatUtil.HTML_RED).append(";");
			}
			data.add(new ParentItemDto(
					category.getId(),
					StringUtils.repeat("\u00a0", depth * 2) + CryptoUtil.decryptWrapper(category.getName(), user),
					sb.toString(),
					category.isIncome(),
					category.getType(),
					category.getPeriodType()));
			sb.setLength(0);
			if (category.getChildren() != null) {
				addCategories(data, category.getChildren(), exclude, user, depth + 1);
			}
		}
	}
}
