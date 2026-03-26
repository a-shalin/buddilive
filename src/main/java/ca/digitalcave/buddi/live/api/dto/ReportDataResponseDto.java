package ca.digitalcave.buddi.live.api.dto;

import java.util.List;
import java.util.Map;

public record ReportDataResponseDto(boolean success, List<Map<String, Object>> data) {
}
