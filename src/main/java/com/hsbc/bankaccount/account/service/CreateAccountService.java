package com.hsbc.bankaccount.account.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.hsbc.bankaccount.account.dto.AccountResponse;
import com.hsbc.bankaccount.account.dto.CreateAccountRequest;

@Service
public class CreateAccountService {

	private final AccountService accountService;

	private final IdempotencyRecordService idempotencyRecords;

	public CreateAccountService(AccountService accountService, IdempotencyRecordService idempotencyRecords) {
		this.accountService = accountService;
		this.idempotencyRecords = idempotencyRecords;
	}

	public AccountResponse create(String idempotencyKey, CreateAccountRequest request) {
		idempotencyKey = IdempotencyKeys.require(idempotencyKey);
		String requestHash = AccountRequestHash.of(request);
		IdempotencyClaim claim;
		try {
			claim = idempotencyRecords.claim(idempotencyKey, requestHash);
		}
		catch (DataIntegrityViolationException exception) {
			return idempotencyRecords.replayOrReject(idempotencyKey, requestHash);
		}
		if (claim.response() != null) {
			return claim.response();
		}
		try {
			return accountService.create(request, claim.recordId());
		}
		catch (RuntimeException exception) {
			idempotencyRecords.abandon(claim.recordId());
			throw exception;
		}
	}

}
