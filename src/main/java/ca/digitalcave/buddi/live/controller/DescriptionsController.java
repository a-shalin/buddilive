package ca.digitalcave.buddi.live.controller;

import ca.digitalcave.buddi.live.db.Transactions;
import ca.digitalcave.buddi.live.model.Split;
import ca.digitalcave.buddi.live.model.Transaction;
import ca.digitalcave.buddi.live.model.User;
import ca.digitalcave.buddi.live.util.CryptoUtil;
import ca.digitalcave.moss.crypto.Crypto.CryptoException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("/data/transactions/descriptions")
public class DescriptionsController {

	@Autowired
	private Transactions transactions;

	@Autowired
	private JsonFactory jsonFactory;

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public StreamingResponseBody get(@AuthenticationPrincipal final User user) {
		try {
			final Map<String, Transaction> transactionsByDescription = new TreeMap<>();
			final List<Transaction> txns = transactions.selectDescriptions(user);
			for (Transaction transaction : txns) {
				final String description = CryptoUtil.decryptWrapper(transaction.getDescription(), user);
				if (transactionsByDescription.get(description) == null) {
					transactionsByDescription.put(description, transaction);
				}
			}

			return outputStream -> {
				try (JsonGenerator generator = jsonFactory.createGenerator(outputStream)) {
					generator.writeStartObject();
					generator.writeBooleanField("success", true);
					generator.writeArrayFieldStart("data");
					for (String description : transactionsByDescription.keySet()) {
						final Transaction t = transactionsByDescription.get(description);
						generator.writeStartObject();
						generator.writeStringField("value", description);
						generator.writeObjectFieldStart("transaction");
						generator.writeStringField("description", description);
						generator.writeStringField("number", CryptoUtil.decryptWrapper(t.getNumber(), user));
						generator.writeArrayFieldStart("splits");
						for (Split s : t.getSplits() != null ? t.getSplits() : new ArrayList<Split>()) {
							generator.writeStartObject();
							final BigDecimal amount = CryptoUtil.decryptWrapperBigDecimal(s.getAmount(), user, false);
							generator.writeStringField("amount", amount.toPlainString());
							generator.writeNumberField("amountNumber", amount);
							generator.writeNumberField("fromId", s.getFromSource());
							generator.writeNumberField("toId", s.getToSource());
							generator.writeStringField("fromType", s.getFromType());
							generator.writeStringField("toType", s.getToType());
							generator.writeEndObject();
						}
						generator.writeEndArray();
						generator.writeEndObject();
						generator.writeEndObject();
					}
					generator.writeEndArray();
					generator.writeEndObject();
					generator.flush();
				}
				catch (CryptoException e) {
					throw new RuntimeException(e);
				}
				catch (Exception e) {
					if (isClientAbort(e)) {
						return;
					}
					if (e instanceof IOException ioException) {
						throw ioException;
					}
					if (e instanceof RuntimeException runtimeException) {
						throw runtimeException;
					}
					throw new RuntimeException(e);
				}
			};
		}
		catch (CryptoException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	private static boolean isClientAbort(final Throwable exception) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof AsyncRequestNotUsableException) {
				return true;
			}
			if ("org.apache.catalina.connector.ClientAbortException".equals(current.getClass().getName())) {
				return true;
			}
			if (current instanceof IOException ioException
					&& ioException.getMessage() != null
					&& ioException.getMessage().toLowerCase().contains("broken pipe")) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}
