package com.hsbc.bankaccount.account.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hsbc.bankaccount.account.dto.AccountResponse;
import com.hsbc.bankaccount.account.entity.IdempotencyRecord;
import com.hsbc.bankaccount.account.entity.IdempotencyStatus;
import com.hsbc.bankaccount.account.exception.IdempotencyInProgressException;
import com.hsbc.bankaccount.account.exception.IdempotencyKeyConflictException;
import com.hsbc.bankaccount.account.repository.IdempotencyRecordRepository;

import tools.jackson.databind.json.JsonMapper;

@Service
public class IdempotencyRecordService {

	static final Duration TIME_TO_LIVE = Duration.ofHours(24);

	private final IdempotencyRecordRepository repository;

	private final JsonMapper jsonMapper;

	public IdempotencyRecordService(IdempotencyRecordRepository repository, JsonMapper jsonMapper) {
		this.repository = repository;
		this.jsonMapper = jsonMapper;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public IdempotencyClaim claim(String idempotencyKey, String requestHash) {
		Instant now = Instant.now();
		return repository.findByIdempotencyKey(idempotencyKey)
				.map(existing -> useExisting(existing, idempotencyKey, requestHash, now))
				.orElseGet(() -> acquire(idempotencyKey, requestHash, now));
	}

	@Transactional
	public void markCompleted(Long recordId, AccountResponse response) {
		IdempotencyRecord record = repository.findById(recordId).orElseThrow();
		record.complete(jsonMapper.writeValueAsString(response));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void abandon(Long recordId) {
		repository.findById(recordId)
				.filter(record -> record.getStatus() == IdempotencyStatus.PROCESSING)
				.ifPresent(repository::delete);
	}

	@Transactional(readOnly = true)
	public AccountResponse replayOrReject(String idempotencyKey, String requestHash) {
		IdempotencyRecord record = repository.findByIdempotencyKey(idempotencyKey)
				.orElseThrow(() -> new DataIntegrityViolationException("Idempotency key " + idempotencyKey + " was not stored"));
		return resolve(record, idempotencyKey, requestHash);
	}

	private IdempotencyClaim useExisting(IdempotencyRecord existing, String idempotencyKey, String requestHash, Instant now) {
		if (existing.getExpiresAt().isAfter(now)) {
			return IdempotencyClaim.replay(resolve(existing, idempotencyKey, requestHash));
		}
		repository.delete(existing);
		repository.flush();
		return acquire(idempotencyKey, requestHash, now);
	}

	private AccountResponse resolve(IdempotencyRecord record, String idempotencyKey, String requestHash) {
		if (!record.getRequestHash().equals(requestHash)) {
			throw new IdempotencyKeyConflictException(idempotencyKey);
		}
		if (record.getStatus() == IdempotencyStatus.COMPLETED) {
			return jsonMapper.readValue(record.getResponseData(), AccountResponse.class);
		}
		throw new IdempotencyInProgressException(idempotencyKey);
	}

	private IdempotencyClaim acquire(String idempotencyKey, String requestHash, Instant now) {
		IdempotencyRecord record = repository.saveAndFlush(
				IdempotencyRecord.processing(idempotencyKey, requestHash, now, TIME_TO_LIVE));
		return IdempotencyClaim.acquired(record.getId());
	}

}
