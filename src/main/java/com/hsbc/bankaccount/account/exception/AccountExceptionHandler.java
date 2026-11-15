package com.hsbc.bankaccount.account.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AccountExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> validationFailed(MethodArgumentNotValidException exception) {
		Map<String, String> fields = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors()
				.forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest()
				.body(new ApiError("validation_failed", "Request validation failed", fields));
	}

	@ExceptionHandler(InvalidPageRequestException.class)
	ResponseEntity<ApiError> invalidPageRequest(InvalidPageRequestException exception) {
		return ResponseEntity.badRequest()
				.body(new ApiError("validation_failed", exception.getMessage()));
	}

	@ExceptionHandler(EmptyUpdateRequestException.class)
	ResponseEntity<ApiError> emptyUpdateRequest(EmptyUpdateRequestException exception) {
		return ResponseEntity.badRequest()
				.body(new ApiError("EMPTY_UPDATE_REQUEST", exception.getMessage()));
	}

	@ExceptionHandler(InvalidAccountHolderNameException.class)
	ResponseEntity<ApiError> invalidAccountHolderName(InvalidAccountHolderNameException exception) {
		return ResponseEntity.badRequest()
				.body(new ApiError("INVALID_ACCOUNT_HOLDER_NAME", exception.getMessage()));
	}

	@ExceptionHandler(InvalidContactInfoException.class)
	ResponseEntity<ApiError> invalidContactInfo(InvalidContactInfoException exception) {
		return ResponseEntity.badRequest()
				.body(new ApiError("INVALID_CONTACT_INFO", exception.getMessage()));
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	ResponseEntity<ApiError> missingIdempotencyKey(MissingRequestHeaderException exception) {
		if (!"Idempotency-Key".equals(exception.getHeaderName())) {
			return ResponseEntity.badRequest()
					.body(new ApiError("validation_failed", exception.getMessage()));
		}
		return ResponseEntity.badRequest()
				.body(new ApiError("missing_idempotency_key", "Idempotency-Key header is required"));
	}

	@ExceptionHandler(InvalidIdempotencyKeyException.class)
	ResponseEntity<ApiError> invalidIdempotencyKey() {
		return ResponseEntity.badRequest()
				.body(new ApiError("invalid_idempotency_key",
						"Idempotency-Key must be 1-64 letters, digits, underscores, or hyphens"));
	}

	@ExceptionHandler(IdempotencyKeyConflictException.class)
	ResponseEntity<ApiError> idempotencyConflict(IdempotencyKeyConflictException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiError("idempotency_conflict", exception.getMessage()));
	}

	@ExceptionHandler(IdempotencyInProgressException.class)
	ResponseEntity<ApiError> idempotencyInProgress(IdempotencyInProgressException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiError("idempotency_in_progress", exception.getMessage()));
	}

	@ExceptionHandler(AccountNotFoundException.class)
	ResponseEntity<ApiError> accountNotFound(AccountNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ApiError("account_not_found", exception.getMessage()));
	}

	@ExceptionHandler(AccountClosedException.class)
	ResponseEntity<ApiError> accountClosed(AccountClosedException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiError("account_closed", exception.getMessage()));
	}

	@ExceptionHandler(InsufficientFundsException.class)
	ResponseEntity<ApiError> insufficientFunds(InsufficientFundsException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiError("insufficient_funds", exception.getMessage()));
	}

	@ExceptionHandler(SameAccountTransferException.class)
	ResponseEntity<ApiError> sameAccountTransfer(SameAccountTransferException exception) {
		return ResponseEntity.badRequest()
				.body(new ApiError("same_account", exception.getMessage()));
	}

}
