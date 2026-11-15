package com.hsbc.bankaccount.account.dto;

import java.math.BigDecimal;

import com.hsbc.bankaccount.account.entity.AccountStatus;
import com.hsbc.bankaccount.account.entity.BankAccount;

public record AccountResponse(
		String accountNumber,
		String holderName,
		String holderId,
		BigDecimal balance,
		AccountStatus status) {

	public static AccountResponse from(BankAccount account) {
		return new AccountResponse(
				account.getAccountNumber(),
				account.getHolderName(),
				account.getHolderId(),
				account.getBalance(),
				account.getStatus());
	}

}
