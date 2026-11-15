package com.hsbc.bankaccount.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.hsbc.bankaccount.account.dto.AccountPageResponse;
import com.hsbc.bankaccount.account.dto.AccountResponse;
import com.hsbc.bankaccount.account.dto.TransferRequest;
import com.hsbc.bankaccount.account.entity.BankAccount;
import com.hsbc.bankaccount.account.exception.InsufficientFundsException;
import com.hsbc.bankaccount.account.repository.BankAccountRepository;
import com.hsbc.bankaccount.account.repository.TransferRepository;

@SpringBootTest
class AccountStressTest {

	private static final int THREADS = 32;

	@Autowired
	private AccountService accountService;

	@Autowired
	private BankAccountRepository accounts;

	@Autowired
	private TransferRepository transfers;

	@DynamicPropertySource
	static void stressDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () ->
				"jdbc:h2:mem:stresstest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000");
		registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
	}

	@BeforeEach
	void deleteSavedRows() {
		clearRows();
	}

	@AfterEach
	void deleteRowsAfterTest() {
		clearRows();
	}

	@Test
	void servesConcurrentAccountReads() throws Exception {
		accounts.saveAndFlush(account("STRESS-READ", "Ada Lovelace", "100.00"));

		List<AccountResponse> responses = runTogether(THREADS, () -> accountService.get("STRESS-READ"));

		assertThat(responses).hasSize(THREADS);
		assertThat(responses).allSatisfy(response -> {
			assertThat(response.accountNumber()).isEqualTo("STRESS-READ");
			assertThat(response.holderName()).isEqualTo("Ada Lovelace");
			assertThat(response.balance()).isEqualByComparingTo("100.00");
		});
	}

	@Test
	void servesConcurrentAccountPages() throws Exception {
		for (int index = 0; index < 30; index++) {
			accounts.save(account("PAGE-%02d".formatted(index), "Holder " + index, "1.00"));
		}
		accounts.flush();

		List<List<AccountPageResponse>> pages = runTogether(THREADS, () -> List.of(
				accountService.list(0, 10),
				accountService.list(1, 10),
				accountService.list(2, 10)));

		assertThat(pages).allSatisfy(result -> {
			assertThat(result).allSatisfy(page -> {
				assertThat(page.totalElements()).isEqualTo(30);
				assertThat(page.totalPages()).isEqualTo(3);
				assertThat(page.accounts()).hasSize(10);
			});
			assertThat(result.get(0).accounts()).extracting(AccountResponse::accountNumber)
					.containsExactly("PAGE-00", "PAGE-01", "PAGE-02", "PAGE-03", "PAGE-04", "PAGE-05", "PAGE-06", "PAGE-07", "PAGE-08", "PAGE-09");
			assertThat(result.get(2).accounts()).extracting(AccountResponse::accountNumber)
					.containsExactly("PAGE-20", "PAGE-21", "PAGE-22", "PAGE-23", "PAGE-24", "PAGE-25", "PAGE-26", "PAGE-27", "PAGE-28", "PAGE-29");
		});
	}

	@Test
	void keepsBalancesConsistentUnderConcurrentTransfers() throws Exception {
		accounts.saveAndFlush(account("STRESS-FROM", "Ada Lovelace", "1000.00"));
		accounts.saveAndFlush(account("STRESS-TO", "Grace Hopper", "0.00"));

		List<Boolean> results = runTogether(THREADS, () -> transfer("STRESS-FROM", "STRESS-TO", "30.00"));

		long successes = results.stream().filter(success -> success).count();
		BigDecimal fromBalance = accounts.findById("STRESS-FROM").orElseThrow().getBalance();
		BigDecimal toBalance = accounts.findById("STRESS-TO").orElseThrow().getBalance();

		assertThat(fromBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
		assertThat(fromBalance.add(toBalance)).isEqualByComparingTo("1000.00");
		assertThat(toBalance).isEqualByComparingTo(new BigDecimal("30.00").multiply(BigDecimal.valueOf(successes)));
		assertThat(transfers.findAll()).hasSize((int) successes);
	}

	@Test
	void completesOppositeTransfersWithoutLosingMoney() throws Exception {
		accounts.saveAndFlush(account("STRESS-LEFT", "Ada Lovelace", "200.00"));
		accounts.saveAndFlush(account("STRESS-RIGHT", "Grace Hopper", "200.00"));

		List<Callable<Boolean>> tasks = new ArrayList<>();
		for (int index = 0; index < THREADS / 2; index++) {
			tasks.add(() -> transfer("STRESS-LEFT", "STRESS-RIGHT", "10.00"));
			tasks.add(() -> transfer("STRESS-RIGHT", "STRESS-LEFT", "10.00"));
		}
		List<Boolean> results = runTogether(tasks);

		long successes = results.stream().filter(success -> success).count();
		BigDecimal leftBalance = accounts.findById("STRESS-LEFT").orElseThrow().getBalance();
		BigDecimal rightBalance = accounts.findById("STRESS-RIGHT").orElseThrow().getBalance();

		assertThat(leftBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
		assertThat(rightBalance).isGreaterThanOrEqualTo(BigDecimal.ZERO);
		assertThat(leftBalance.add(rightBalance)).isEqualByComparingTo("400.00");
		assertThat(transfers.findAll()).hasSize((int) successes);
	}

	private boolean transfer(String fromAccountNumber, String toAccountNumber, String amount) {
		try {
			accountService.transfer(new TransferRequest(fromAccountNumber, toAccountNumber, new BigDecimal(amount)));
			return true;
		}
		catch (InsufficientFundsException exception) {
			return false;
		}
	}

	private BankAccount account(String accountNumber, String holderName, String balance) {
		return new BankAccount(accountNumber, holderName, "110101199001011234", "ada@example.com", new BigDecimal(balance));
	}

	private <T> List<T> runTogether(int threads, Callable<T> task) throws Exception {
		List<Callable<T>> tasks = new ArrayList<>();
		for (int index = 0; index < threads; index++) {
			tasks.add(task);
		}
		return runTogether(tasks);
	}

	private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(executor.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			executor.shutdownNow();
		}
	}

	private void clearRows() {
		transfers.deleteAll();
		accounts.deleteAll();
	}

}
