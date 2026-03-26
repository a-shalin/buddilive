package ca.digitalcave.buddi.live.api.dto;

import java.util.List;
import java.util.Map;

public record DataBackupResponseDto(
		List<Map<String, Object>> accounts,
		List<Map<String, Object>> categories,
		List<Map<String, Object>> entries,
		List<Map<String, Object>> transactions,
		List<Map<String, Object>> scheduledTransactions) {
}
