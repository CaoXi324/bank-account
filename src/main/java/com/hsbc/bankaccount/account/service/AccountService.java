package com.hsbc.bankaccount.account.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hsbc.bankaccount.account.dto.AccountResponse;
import com.hsbc.bankaccount.account.dto.CreateAccountRequest;
import com.hsbc.bankaccount.account.entity.BankAccount;
import com.hsbc.bankaccount.account.repository.BankAccountRepository;

import jakarta.persistence.EntityManager;

@Service
public class AccountService {

	private static final int MAX_ATTEMPTS = 5;

	private final BankAccountRepository repository;

	private final EntityManager entityManager;

	private final AccountNumberGenerator accountNumberGenerator;

	private final IdempotencyRecordService idempotencyRecords;

	public AccountService(BankAccountRepository repository, EntityManager entityManager,
			AccountNumberGenerator accountNumberGenerator, IdempotencyRecordService idempotencyRecords) {
		this.repository = repository;
		this.entityManager = entityManager;
		this.accountNumberGenerator = accountNumberGenerator;
		this.idempotencyRecords = idempotencyRecords;
	}

	@Transactional
	public AccountResponse create(CreateAccountRequest request, Long idempotencyRecordId) {
		BankAccount account = new BankAccount(
				nextAvailableAccountNumber(),
				request.holderName(),
				request.idCardNumber(),
				request.phoneNumber(),
				request.balance());
		entityManager.persist(account);
		entityManager.flush();
		AccountResponse response = AccountResponse.from(account);
		idempotencyRecords.markCompleted(idempotencyRecordId, response);
		return response;
	}

	private String nextAvailableAccountNumber() {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			String accountNumber = accountNumberGenerator.next();
			if (!repository.existsById(accountNumber)) {
				return accountNumber;
			}
		}
		throw new IllegalStateException("Unable to allocate a unique account number");
	}

}
