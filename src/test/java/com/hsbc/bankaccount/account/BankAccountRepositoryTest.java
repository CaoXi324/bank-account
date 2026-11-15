package com.hsbc.bankaccount.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

@DataJpaTest
class BankAccountRepositoryTest {

	@Autowired
	private BankAccountRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void savesAndLoadsAccountByAccountNumber() {
		repository.save(new BankAccount("ACC-1001", "Ada Lovelace", "110101199001011234", "13800138000", new BigDecimal("1250.50")));

		BankAccount loaded = repository.findById("ACC-1001").orElseThrow();

		assertThat(loaded.getHolderName()).isEqualTo("Ada Lovelace");
		assertThat(loaded.getIdCardNumber()).isEqualTo("110101199001011234");
		assertThat(loaded.getPhoneNumber()).isEqualTo("13800138000");
		assertThat(loaded.getBalance()).isEqualByComparingTo("1250.50");
	}

	@Test
	void rejectsDuplicateAccountNumber() {
		entityManager.persist(new BankAccount("ACC-1001", "Ada Lovelace", "110101199001011234", "13800138000", new BigDecimal("10.00")));
		entityManager.flush();
		entityManager.clear();

		assertThatThrownBy(() -> {
			entityManager.persist(new BankAccount("ACC-1001", "Grace Hopper", "110101199002021234", "13900139000", new BigDecimal("20.00")));
			entityManager.flush();
		}).isInstanceOf(PersistenceException.class);
	}

}
