package com.hsbc.bankaccount.account.exception;

public class InvalidContactInfoException extends RuntimeException {

	public InvalidContactInfoException() {
		super("contactInfo must be 1-200 characters");
	}

}
