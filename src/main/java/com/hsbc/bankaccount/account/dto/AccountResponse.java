package com.hsbc.bankaccount.account.dto;

import java.math.BigDecimal;

import com.hsbc.bankaccount.account.entity.BankAccount;

public record AccountResponse(
		String accountNumber,
		String holderName,
		String idCardNumber,
		String phoneNumber,
		BigDecimal balance) {

	public static AccountResponse from(BankAccount account) {
		return new AccountResponse(
				account.getAccountNumber(),
				account.getHolderName(),
				account.getIdCardNumber(),
				account.getPhoneNumber(),
				account.getBalance());
	}

}
