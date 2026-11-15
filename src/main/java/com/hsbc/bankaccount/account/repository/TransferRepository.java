package com.hsbc.bankaccount.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hsbc.bankaccount.account.entity.Transfer;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
}
