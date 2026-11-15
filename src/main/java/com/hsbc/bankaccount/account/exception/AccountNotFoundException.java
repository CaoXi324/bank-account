package com.hsbc.bankaccount.account.exception;

public class AccountNotFoundException extends RuntimeException {

	public AccountNotFoundException(String accountNumber) {
		super("Account number " + accountNumber + " does not exist");
	}

}
