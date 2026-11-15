package com.hsbc.bankaccount.account.exception;

public class EmptyUpdateRequestException extends RuntimeException {

	public EmptyUpdateRequestException() {
		super("At least one of holderName or contactInfo is required");
	}

}
