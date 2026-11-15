package com.hsbc.bankaccount.account.service;

import java.util.regex.Pattern;

import com.hsbc.bankaccount.account.exception.InvalidIdempotencyKeyException;

final class IdempotencyKeys {

	private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

	private IdempotencyKeys() {
	}

	static String require(String idempotencyKey) {
		if (idempotencyKey == null || !PATTERN.matcher(idempotencyKey).matches()) {
			throw new InvalidIdempotencyKeyException();
		}
		return idempotencyKey;
	}

}
