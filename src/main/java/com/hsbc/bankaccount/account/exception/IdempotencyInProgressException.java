package com.hsbc.bankaccount.account.exception;

public class IdempotencyInProgressException extends RuntimeException {

	public IdempotencyInProgressException(String idempotencyKey) {
		super("Idempotency key " + idempotencyKey + " is already being processed");
	}

}
