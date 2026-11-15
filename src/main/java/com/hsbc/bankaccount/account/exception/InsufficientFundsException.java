package com.hsbc.bankaccount.account.exception;

public class InsufficientFundsException extends RuntimeException {

	public InsufficientFundsException(String accountNumber) {
		super("Account number " + accountNumber + " has insufficient funds");
	}

}
