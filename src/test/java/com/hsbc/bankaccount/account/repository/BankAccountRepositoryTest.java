package com.hsbc.bankaccount.account.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.hsbc.bankaccount.account.entity.AccountStatus;
import com.hsbc.bankaccount.account.entity.BankAccount;

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
		repository.save(new BankAccount("ACC-1001", "Ada Lovelace", "110101199001011234", "ada@example.com", new BigDecimal("1250.50")));

		BankAccount loaded = repository.findById("ACC-1001").orElseThrow();

		assertThat(loaded.getHolderName()).isEqualTo("Ada Lovelace");
		assertThat(loaded.getHolderId()).isEqualTo("110101199001011234");
		assertThat(loaded.getContactInfo()).isEqualTo("ada@example.com");
		assertThat(loaded.getBalance()).isEqualByComparingTo("1250.50");
		assertThat(loaded.getStatus()).isEqualTo(AccountStatus.OPEN);
	}

	@Test
	void rejectsDuplicateAccountNumber() {
		entityManager.persist(new BankAccount("ACC-1001", "Ada Lovelace", "110101199001011234", "ada@example.com", new BigDecimal("10.00")));
		entityManager.flush();
		entityManager.clear();

		assertThatThrownBy(() -> {
			entityManager.persist(new BankAccount("ACC-1001", "Grace Hopper", "110101199002021234", "grace@example.com", new BigDecimal("20.00")));
			entityManager.flush();
		}).isInstanceOf(PersistenceException.class);
	}

}
