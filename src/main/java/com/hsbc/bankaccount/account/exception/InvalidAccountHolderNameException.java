package com.hsbc.bankaccount.account.exception;

public class InvalidAccountHolderNameException extends RuntimeException {

	public InvalidAccountHolderNameException() {
		super("holderName must be 1-100 characters");
	}

}
