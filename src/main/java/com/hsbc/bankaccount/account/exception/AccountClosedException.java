package com.hsbc.bankaccount.account.exception;

public class AccountClosedException extends RuntimeException {

	public AccountClosedException(String accountNumber) {
		super("Account number " + accountNumber + " is closed");
	}

}
