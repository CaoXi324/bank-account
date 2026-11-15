package com.hsbc.bankaccount.account.entity;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
	private String idempotencyKey;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(name = "response_data", length = 1000)
	private String responseData;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private IdempotencyStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected IdempotencyRecord() {
	}

	private IdempotencyRecord(String idempotencyKey, String requestHash, IdempotencyStatus status, String responseData,
			Instant createdAt, Instant expiresAt) {
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.status = status;
		this.responseData = responseData;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public static IdempotencyRecord processing(String idempotencyKey, String requestHash, Instant createdAt, Duration timeToLive) {
		return new IdempotencyRecord(idempotencyKey, requestHash, IdempotencyStatus.PROCESSING, null, createdAt,
				createdAt.plus(timeToLive));
	}

	public static IdempotencyRecord completed(String idempotencyKey, String requestHash, String responseData,
			Instant createdAt, Instant expiresAt) {
		return new IdempotencyRecord(idempotencyKey, requestHash, IdempotencyStatus.COMPLETED, responseData, createdAt, expiresAt);
	}

	public void complete(String responseData) {
		this.responseData = responseData;
		this.status = IdempotencyStatus.COMPLETED;
	}

	public Long getId() {
		return id;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public String getResponseData() {
		return responseData;
	}

	public IdempotencyStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

}
