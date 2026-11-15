package com.hsbc.bankaccount.account.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hsbc.bankaccount.account.entity.BankAccount;

import jakarta.persistence.LockModeType;

public interface BankAccountRepository extends JpaRepository<BankAccount, String> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select account from BankAccount account where account.accountNumber = :accountNumber")
	Optional<BankAccount> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

}
