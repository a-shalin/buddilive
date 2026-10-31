package ca.digitalcave.buddi.live.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransactionRecordDto(long id, String uuid, String dateIso, String description, String number, List<Split> splits) {

	public record Split(Long id, BigDecimal amountNumber, int fromId, int toId, String memo) {
	}
}
