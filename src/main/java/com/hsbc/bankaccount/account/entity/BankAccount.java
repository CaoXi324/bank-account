package com.hsbc.bankaccount.account.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

	@Column(name = "id_card_number", nullable = false, length = 18)
	private String idCardNumber;

	@Column(name = "phone_number", nullable = false, length = 11)
	private String phoneNumber;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal balance;

	protected BankAccount() {
	}

	public BankAccount(String accountNumber, String holderName, String idCardNumber, String phoneNumber, BigDecimal balance) {
		this.accountNumber = accountNumber;
		this.holderName = holderName;
		this.idCardNumber = idCardNumber;
		this.phoneNumber = phoneNumber;
		this.balance = balance;
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

	public String getIdCardNumber() {
		return idCardNumber;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void setBalance(BigDecimal balance) {
		this.balance = balance;
	}

}
