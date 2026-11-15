package com.hsbc.bankaccount.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountNumberGeneratorTest {

	private final AccountNumberGenerator generator = new AccountNumberGenerator();

	@Test
	void generatesSixteenDigitAccountNumber() {
		String accountNumber = generator.next();

		assertThat(accountNumber).hasSize(AccountNumberGenerator.LENGTH);
		assertThat(accountNumber).matches("[1-9]\\d{15}");
	}

}
