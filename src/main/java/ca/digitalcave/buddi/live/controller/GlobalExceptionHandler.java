package ca.digitalcave.buddi.live.controller;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;

import ca.digitalcave.buddi.live.api.dto.ErrorResponseDto;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger logger = Logger.getLogger(GlobalExceptionHandler.class.getName());

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ErrorResponseDto> handleResponseStatus(final ResponseStatusException e) {
		return ResponseEntity.status(e.getStatusCode()).body(new ErrorResponseDto(false, e.getReason()));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponseDto> handleMethodNotSupported(final HttpRequestMethodNotSupportedException e) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new ErrorResponseDto(false, e.getMessage()));
	}

	@ExceptionHandler(AsyncRequestNotUsableException.class)
	public ResponseEntity<ErrorResponseDto> handleAsyncRequestNotUsable(final AsyncRequestNotUsableException e) {
		if (isClientAbort(e)) {
			logger.log(Level.FINE, "Client disconnected during async response");
			return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
		}

		logger.log(Level.WARNING, "Unhandled exception", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDto(false, e.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponseDto> handleGeneral(final Exception e) {
		if (isClientAbort(e)) {
			logger.log(Level.FINE, "Client disconnected during response");
			return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
		}

		logger.log(Level.WARNING, "Unhandled exception", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDto(false, e.getMessage()));
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
