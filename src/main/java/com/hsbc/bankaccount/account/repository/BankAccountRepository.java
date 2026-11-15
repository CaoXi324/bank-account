package com.hsbc.bankaccount.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hsbc.bankaccount.account.entity.BankAccount;

public interface BankAccountRepository extends JpaRepository<BankAccount, String> {
}
