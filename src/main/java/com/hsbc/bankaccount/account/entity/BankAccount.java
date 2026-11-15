package com.hsbc.bankaccount.account.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bank_accounts")
public class BankAccount {

	@Id
	@Column(name = "account_number", nullable = false, length = 34)
	private String accountNumber;

	@Column(name = "holder_name", nullable = false, length = 100)
	private String holderName;

	@Column(name = "holder_id", nullable = false, length = 18)
	private String holderId;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal balance;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountStatus status;

	protected BankAccount() {
	}

	public BankAccount(String accountNumber, String holderName, String holderId, BigDecimal balance) {
		this.accountNumber = accountNumber;
		this.holderName = holderName;
		this.holderId = holderId;
		this.balance = balance;
		this.status = AccountStatus.OPEN;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public String getHolderName() {
		return holderName;
	}

	public void setHolderName(String holderName) {
		this.holderName = holderName;
	}

	public String getHolderId() {
		return holderId;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public void close() {
		this.status = AccountStatus.CLOSED;
	}

}
