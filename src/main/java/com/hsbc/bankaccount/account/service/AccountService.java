package com.hsbc.bankaccount.account.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hsbc.bankaccount.account.dto.AccountPageResponse;
import com.hsbc.bankaccount.account.dto.AccountResponse;
import com.hsbc.bankaccount.account.dto.CreateAccountRequest;
import com.hsbc.bankaccount.account.dto.UpdateAccountRequest;
import com.hsbc.bankaccount.account.entity.AccountStatus;
import com.hsbc.bankaccount.account.entity.BankAccount;
import com.hsbc.bankaccount.account.exception.AccountClosedException;
import com.hsbc.bankaccount.account.exception.AccountNotFoundException;
import com.hsbc.bankaccount.account.exception.EmptyUpdateRequestException;
import com.hsbc.bankaccount.account.exception.InvalidAccountHolderNameException;
import com.hsbc.bankaccount.account.exception.InvalidContactInfoException;
import com.hsbc.bankaccount.account.exception.InvalidPageRequestException;
import com.hsbc.bankaccount.account.repository.BankAccountRepository;

import jakarta.persistence.EntityManager;

@Service
public class AccountService {

	private static final int MAX_ATTEMPTS = 5;

	private static final int MAX_PAGE_SIZE = 100;

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
				request.holderId(),
				request.contactInfo(),
				request.balance());
		entityManager.persist(account);
		entityManager.flush();
		AccountResponse response = AccountResponse.from(account);
		idempotencyRecords.markCompleted(idempotencyRecordId, response);
		return response;
	}

	@Transactional(readOnly = true)
	public AccountResponse get(String accountNumber) {
		return repository.findById(accountNumber)
				.map(AccountResponse::from)
				.orElseThrow(() -> new AccountNotFoundException(accountNumber));
	}

	@Transactional(readOnly = true)
	public AccountPageResponse list(int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPageRequestException();
		}
		return AccountPageResponse.from(repository.findAll(PageRequest.of(page, size, Sort.by("accountNumber"))));
	}

	@Transactional
	public AccountResponse update(String accountNumber, UpdateAccountRequest request) {
		validateUpdate(request);
		BankAccount account = repository.findById(accountNumber)
				.orElseThrow(() -> new AccountNotFoundException(accountNumber));
		if (account.getStatus() == AccountStatus.CLOSED) {
			throw new AccountClosedException(accountNumber);
		}
		if (request.holderName() != null) {
			account.setHolderName(request.holderName());
		}
		if (request.contactInfo() != null) {
			account.setContactInfo(request.contactInfo());
		}
		return AccountResponse.from(account);
	}

	@Transactional
	public void delete(String accountNumber) {
		BankAccount account = repository.findById(accountNumber)
				.orElseThrow(() -> new AccountNotFoundException(accountNumber));
		if (account.getStatus() == AccountStatus.CLOSED) {
			throw new AccountClosedException(accountNumber);
		}
		account.close();
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

	private void validateUpdate(UpdateAccountRequest request) {
		if (request.holderName() == null && request.contactInfo() == null) {
			throw new EmptyUpdateRequestException();
		}
		if (request.holderName() != null && !isWithinLength(request.holderName(), 100)) {
			throw new InvalidAccountHolderNameException();
		}
		if (request.contactInfo() != null && !isWithinLength(request.contactInfo(), 200)) {
			throw new InvalidContactInfoException();
		}
	}

	private boolean isWithinLength(String value, int maxLength) {
		return !value.isBlank() && value.length() <= maxLength;
	}

}
