package com.hsbc.bankaccount.account.service;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hsbc.bankaccount.account.dto.AccountPageResponse;
import com.hsbc.bankaccount.account.dto.AccountResponse;
import com.hsbc.bankaccount.account.dto.CreateAccountRequest;
import com.hsbc.bankaccount.account.dto.TransferRequest;
import com.hsbc.bankaccount.account.dto.TransferResponse;
import com.hsbc.bankaccount.account.dto.UpdateAccountRequest;
import com.hsbc.bankaccount.account.entity.AccountStatus;
import com.hsbc.bankaccount.account.entity.BankAccount;
import com.hsbc.bankaccount.account.entity.Transfer;
import com.hsbc.bankaccount.account.exception.AccountClosedException;
import com.hsbc.bankaccount.account.exception.AccountNotFoundException;
import com.hsbc.bankaccount.account.exception.EmptyUpdateRequestException;
import com.hsbc.bankaccount.account.exception.InsufficientFundsException;
import com.hsbc.bankaccount.account.exception.InvalidAccountHolderNameException;
import com.hsbc.bankaccount.account.exception.InvalidContactInfoException;
import com.hsbc.bankaccount.account.exception.InvalidPageRequestException;
import com.hsbc.bankaccount.account.exception.SameAccountTransferException;
import com.hsbc.bankaccount.account.repository.BankAccountRepository;
import com.hsbc.bankaccount.account.repository.TransferRepository;

import jakarta.persistence.EntityManager;

@Service
public class AccountService {

	private static final int MAX_ATTEMPTS = 5;

	private static final int MAX_PAGE_SIZE = 100;

	private final BankAccountRepository repository;

	private final TransferRepository transfers;

	private final EntityManager entityManager;

	private final AccountNumberGenerator accountNumberGenerator;

	private final IdempotencyRecordService idempotencyRecords;

	public AccountService(BankAccountRepository repository, TransferRepository transfers, EntityManager entityManager,
			AccountNumberGenerator accountNumberGenerator, IdempotencyRecordService idempotencyRecords) {
		this.repository = repository;
		this.transfers = transfers;
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
	public TransferResponse transfer(TransferRequest request) {
		String fromAccountNumber = request.fromAccountNumber();
		String toAccountNumber = request.toAccountNumber();
		BigDecimal amount = request.amount();
		if (fromAccountNumber.equals(toAccountNumber)) {
			throw new SameAccountTransferException();
		}
		String firstNumber = fromAccountNumber.compareTo(toAccountNumber) < 0 ? fromAccountNumber : toAccountNumber;
		String secondNumber = firstNumber.equals(fromAccountNumber) ? toAccountNumber : fromAccountNumber;
		BankAccount first = repository.findByAccountNumberForUpdate(firstNumber)
				.orElseThrow(() -> new AccountNotFoundException(firstNumber));
		BankAccount second = repository.findByAccountNumberForUpdate(secondNumber)
				.orElseThrow(() -> new AccountNotFoundException(secondNumber));
		BankAccount source = accountWithNumber(first, second, fromAccountNumber);
		BankAccount destination = accountWithNumber(first, second, toAccountNumber);
		if (source.getStatus() == AccountStatus.CLOSED) {
			throw new AccountClosedException(source.getAccountNumber());
		}
		if (destination.getStatus() == AccountStatus.CLOSED) {
			throw new AccountClosedException(destination.getAccountNumber());
		}
		if (source.getBalance().compareTo(amount) < 0) {
			throw new InsufficientFundsException(source.getAccountNumber());
		}
		source.setBalance(source.getBalance().subtract(amount));
		destination.setBalance(destination.getBalance().add(amount));
		Transfer transfer = transfers.saveAndFlush(new Transfer(
				fromAccountNumber,
				toAccountNumber,
				amount,
				Instant.now()));
		return new TransferResponse(
				transfer.getId(),
				AccountResponse.from(source),
				AccountResponse.from(destination),
				transfer.getAmount(),
				transfer.getCreatedAt());
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

	private BankAccount accountWithNumber(BankAccount first, BankAccount second, String accountNumber) {
		return first.getAccountNumber().equals(accountNumber) ? first : second;
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
