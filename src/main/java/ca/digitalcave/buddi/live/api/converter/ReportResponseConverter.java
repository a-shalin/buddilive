package ca.digitalcave.buddi.live.api.converter;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import ca.digitalcave.buddi.live.api.dto.ReportDataResponseDto;

@Component
public class ReportResponseConverter {

	public ReportDataResponseDto convert(final List<Map<String, Object>> data) {
		return new ReportDataResponseDto(true, data);
	}
}
