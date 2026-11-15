package com.hsbc.bankaccount.account.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.hsbc.bankaccount.account.entity.IdempotencyRecord;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

@DataJpaTest
class IdempotencyRecordRepositoryTest {

	@Autowired
	private EntityManager entityManager;

	@Test
	void rejectsDuplicateIdempotencyKey() {
		Instant now = Instant.now();
		entityManager.persist(IdempotencyRecord.processing("key-1", "a".repeat(64), now, Duration.ofHours(24)));
		entityManager.flush();
		entityManager.clear();

		assertThatThrownBy(() -> {
			entityManager.persist(IdempotencyRecord.processing("key-1", "b".repeat(64), now, Duration.ofHours(24)));
			entityManager.flush();
		}).isInstanceOf(PersistenceException.class);
	}

}
