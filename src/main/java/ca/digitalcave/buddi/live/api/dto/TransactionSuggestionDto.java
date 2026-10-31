package ca.digitalcave.buddi.live.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransactionSuggestionDto(String description, List<Split> splits) {

	public record Split(BigDecimal amountNumber, int fromId, int toId, String fromType, String toType) {
	}
}
