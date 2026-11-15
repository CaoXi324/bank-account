package com.hsbc.bankaccount.account.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transfers")
public class Transfer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "from_account_number", nullable = false, length = 34)
	private String fromAccountNumber;

	@Column(name = "to_account_number", nullable = false, length = 34)
	private String toAccountNumber;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Transfer() {
	}

	public Transfer(String fromAccountNumber, String toAccountNumber, BigDecimal amount, Instant createdAt) {
		this.fromAccountNumber = fromAccountNumber;
		this.toAccountNumber = toAccountNumber;
		this.amount = amount;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public String getFromAccountNumber() {
		return fromAccountNumber;
	}

	public String getToAccountNumber() {
		return toAccountNumber;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
