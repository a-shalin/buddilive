package ca.digitalcave.buddi.live.api.converter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import ca.digitalcave.buddi.live.api.dto.ScheduledTransactionsExecuteResponseDto;
import ca.digitalcave.buddi.live.api.dto.ScheduledTransactionsResponseDto;
import ca.digitalcave.buddi.live.api.dto.ScheduledTransactionsResponseDto.ScheduledSplitDto;
import ca.digitalcave.buddi.live.api.dto.ScheduledTransactionsResponseDto.ScheduledTransactionDto;
import ca.digitalcave.buddi.live.model.ScheduledTransaction;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.buddi.live.util.FormatUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;

@Component
public class ScheduledTransactionsResponseConverter {

	public ScheduledTransactionsResponseDto convert(final User user,
			final List<ScheduledTransaction> scheduledTransactions) throws CryptoException {
		scheduledTransactions.sort((o1, o2) -> {
			if (o1 == null || o2 == null) {
				return 0;
			}
			try {
				return CryptoUtil.decryptWrapper(o1.getScheduleName(), user)
						.compareTo(CryptoUtil.decryptWrapper(o2.getScheduleName(), user));
			}
			catch (final CryptoException e) {
				return 0;
			}
		});

		final List<ScheduledTransactionDto> data = new ArrayList<>();
		for (final ScheduledTransaction transaction : scheduledTransactions) {
			final List<ScheduledSplitDto> splits = new ArrayList<>();
			for (final Split split : transaction.getSplits()) {
				final BigDecimal amount = CryptoUtil.decryptWrapperBigDecimal(split.getAmount(), user, false);
				splits.add(new ScheduledSplitDto(
						split.getId(),
						FormatUtil.formatCurrency(amount, user),
						amount,
						split.getFromSource(),
						split.getToSource(),
						CryptoUtil.decryptWrapper(split.getMemo(), user)));
			}

			data.add(new ScheduledTransactionDto(
					transaction.getId(),
					CryptoUtil.decryptWrapper(transaction.getScheduleName(), user),
					CryptoUtil.decryptWrapper(transaction.getDescription(), user),
					CryptoUtil.decryptWrapper(transaction.getNumber(), user),
					transaction.getScheduleDay(),
					transaction.getScheduleWeek(),
					transaction.getScheduleMonth(),
					FormatUtil.formatDateInternal(transaction.getStartDate()),
					FormatUtil.formatDateInternal(transaction.getEndDate()),
					transaction.getFrequencyType(),
					FormatUtil.formatDateInternal(transaction.getLastCreatedDate()),
					CryptoUtil.decryptWrapper(transaction.getMessage(), user),
					splits.isEmpty() ? null : splits));
		}

		return new ScheduledTransactionsResponseDto(true, scheduledTransactions.size(), data);
	}

	public ScheduledTransactionsExecuteResponseDto convertExecute(final String messages) {
		return new ScheduledTransactionsExecuteResponseDto(true, messages);
	}
}
