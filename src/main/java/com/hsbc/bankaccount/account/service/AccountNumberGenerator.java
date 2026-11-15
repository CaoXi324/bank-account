package com.hsbc.bankaccount.account.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class AccountNumberGenerator {

	static final int LENGTH = 16;

	private final SecureRandom random = new SecureRandom();

	public String next() {
		StringBuilder number = new StringBuilder(LENGTH);
		number.append(random.nextInt(9) + 1);
		for (int i = 1; i < LENGTH; i++) {
			number.append(random.nextInt(10));
		}
		return number.toString();
	}

}
