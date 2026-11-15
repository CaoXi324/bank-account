package com.hsbc.bankaccount.account.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hsbc.bankaccount.account.entity.IdempotencyRecord;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

	Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

}
