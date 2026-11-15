package com.hsbc.bankaccount.account.exception;

public class InvalidPageRequestException extends RuntimeException {

	public InvalidPageRequestException() {
		super("page must be 0 or greater and size must be between 1 and 100");
	}

}
