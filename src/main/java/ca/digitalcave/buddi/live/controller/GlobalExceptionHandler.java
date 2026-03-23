package ca.digitalcave.buddi.live.controller;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger logger = Logger.getLogger(GlobalExceptionHandler.class.getName());

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<String> handleResponseStatus(ResponseStatusException e) {
		final JSONObject result = new JSONObject();
		result.put("success", false);
		if (e.getReason() != null) {
			result.put("msg", e.getReason());
		}
		return ResponseEntity.status(e.getStatusCode()).body(result.toString());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleGeneral(Exception e) {
		logger.log(Level.WARNING, "Unhandled exception", e);
		final JSONObject result = new JSONObject();
		result.put("success", false);
		result.put("msg", e.getMessage());
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result.toString());
	}
}