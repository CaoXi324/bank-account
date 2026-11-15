package com.hsbc.bankaccount.account.service;

import com.hsbc.bankaccount.account.dto.AccountResponse;

final class IdempotencyClaim {

	private final Long recordId;

	private final AccountResponse response;

	private IdempotencyClaim(Long recordId, AccountResponse response) {
		this.recordId = recordId;
		this.response = response;
	}

	static IdempotencyClaim acquired(Long recordId) {
		return new IdempotencyClaim(recordId, null);
	}

	static IdempotencyClaim replay(AccountResponse response) {
		return new IdempotencyClaim(null, response);
	}

	Long recordId() {
		return recordId;
	}

	AccountResponse response() {
		return response;
	}

}
