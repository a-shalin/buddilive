package ca.digitalcave.buddi.live.api.dto.request;

import java.util.List;

public record TransactionsSyncRequestDto(int version, List<Account> accounts) {

	public record Account(int sourceId, String bucketHashes) {
	}
}
