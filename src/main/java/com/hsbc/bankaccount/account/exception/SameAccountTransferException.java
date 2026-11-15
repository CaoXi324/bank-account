package com.hsbc.bankaccount.account.exception;

public class SameAccountTransferException extends RuntimeException {

	public SameAccountTransferException() {
		super("Source and destination account numbers must be different");
	}

}
