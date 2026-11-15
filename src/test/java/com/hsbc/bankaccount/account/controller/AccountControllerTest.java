package com.hsbc.bankaccount.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.hsbc.bankaccount.account.dto.CreateAccountRequest;
import com.hsbc.bankaccount.account.entity.AccountStatus;
import com.hsbc.bankaccount.account.entity.BankAccount;
import com.hsbc.bankaccount.account.entity.IdempotencyRecord;
import com.hsbc.bankaccount.account.entity.IdempotencyStatus;
import com.hsbc.bankaccount.account.repository.BankAccountRepository;
import com.hsbc.bankaccount.account.repository.IdempotencyRecordRepository;
import com.hsbc.bankaccount.account.service.AccountRequestHash;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

	private static final String ACCOUNT_NUMBER = "[1-9]\\d{15}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private BankAccountRepository repository;

	@Autowired
	private IdempotencyRecordRepository idempotencyRecords;

	@AfterEach
	void deleteSavedRows() {
		idempotencyRecords.deleteAll();
		repository.deleteAll();
	}

	@Test
	void createsAccountWithGeneratedAccountNumber() throws Exception {
		MvcResult result = mockMvc.perform(post("/accounts")
				.header("Idempotency-Key", "create-ada")
				.contentType(MediaType.APPLICATION_JSON)
				.content(accountJson("Ada Lovelace", "110101199001011234", "1250.50")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accountNumber").value(matchesPattern(ACCOUNT_NUMBER)))
				.andExpect(header().string("Location", matchesPattern("http://localhost/accounts/" + ACCOUNT_NUMBER)))
				.andExpect(jsonPath("$.holderName").value("Ada Lovelace"))
				.andExpect(jsonPath("$.holderId").value("110101199001011234"))
				.andExpect(jsonPath("$.balance").value(1250.50))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andReturn();

		String accountNumber = JsonPath.read(result.getResponse().getContentAsString(), "$.accountNumber");
		assertThat(repository.findById(accountNumber)).isPresent();
		assertThat(idempotencyRecords.findByIdempotencyKey("create-ada")).get()
				.extracting(IdempotencyRecord::getStatus)
				.isEqualTo(IdempotencyStatus.COMPLETED);
	}

	@Test
	void assignsDifferentAccountNumbersToEachAccount() throws Exception {
		String first = createAccount("key-ada", "Ada Lovelace", "110101199001011234", "10.00");
		String second = createAccount("key-grace", "Grace Hopper", "110101199002021234", "20.00");

		assertThat(first).isNotEqualTo(second);
		assertThat(repository.findAll()).hasSize(2);
	}

	@Test
	void rejectsMissingRequiredFields() throws Exception {
		mockMvc.perform(post("/accounts")
				.header("Idempotency-Key", "missing-fields")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.fields.holderName").exists())
				.andExpect(jsonPath("$.fields.holderId").exists())
				.andExpect(jsonPath("$.fields.balance").exists())
				.andExpect(jsonPath("$.fields.phoneNumber").doesNotExist())
				.andExpect(jsonPath("$.fields.accountNumber").doesNotExist());
	}

	@Test
	void rejectsInvalidHolderIdAndBalance() throws Exception {
		mockMvc.perform(post("/accounts")
				.header("Idempotency-Key", "invalid-fields")
				.contentType(MediaType.APPLICATION_JSON)
				.content(accountJson("Ada Lovelace", "123", "-1.00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.fields.holderId").value("must be an 18-digit ID card number"))
				.andExpect(jsonPath("$.fields.balance").exists());
	}

	@Test
	void returnsTheOriginalAccountWhenTheIdempotencyKeyIsReused() throws Exception {
		String accountNumber = createAccount("8f72a1c9-1234", "Ada Lovelace", "110101199001011234", "10.00");

		mockMvc.perform(post("/accounts")
				.header("Idempotency-Key", "8f72a1c9-1234")
				.contentType(MediaType.APPLICATION_JSON)
				.content(accountJson("Ada Lovelace", "110101199001011234", "10.0")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accountNumber").value(accountNumber))
				.andExpect(header().string("Location", "http://localhost/accounts/" + accountNumber));

		assertThat(repository.findAll()).hasSize(1);
		assertThat(idempotencyRecords.findAll()).hasSize(1);
	}

	@Test
	void rejectsTheSameIdempotencyKeyForADifferentRequest() throws Exception {
		String accountNumber = createAccount("8f72a1c9-1234", "Ada Lovelace", "110101199001011234", "10.00");

		mockMvc.perform(post("/accounts")
				.header("Idempotency-Key", "8f72a1c9-1234")
				.contentType(MediaType.APPLICATION_JSON)
				.content(accountJson("Grace Hopper", "110101199002021234", "20.00")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("idempotency_conflict"));

		assertThat(repository.findAll()).hasSize(1);
		assertThat(repository.findById(accountNumber)).get()
				.extracting(BankAccount::getHolderName)
				.isEqualTo("Ada Lovelace");
	}

	@Test
	void rejectsAnIdempotencyKeyThatIsStillProcessing() throws Exception {
		CreateAccountRequest request = new CreateAccountRequest("Ada Lovelace", "110101199001011234", new BigDecimal("10.00"));
		idempotencyRecords.saveAndFlush(IdempotencyRecord.processing("busy-key", AccountRequestHash.of(request), Instant.now(), Duration.ofHours(24)));

		mockMvc.perform(post("/accounts")
				.header("Idempotency-Key", "busy-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(accountJson("Ada Lovelace", "110101199001011234", "10.00")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("idempotency_in_progress"));

		assertThat(repository.findAll()).isEmpty();
	}

	@Test
	void createsANewAccountWhenTheIdempotencyRecordHasExpired() throws Exception {
		Instant expiredAt = Instant.now().minus(Duration.ofHours(1));
		idempotencyRecords.saveAndFlush(IdempotencyRecord.completed(
				"expired-key",
				"a".repeat(64),
				"{\"accountNumber\":\"1000000000000001\"}",
				expiredAt.minus(Duration.ofHours(24)),
				expiredAt));

		mockMvc.perform(post("/accounts")
				.header("Idempotency-Key", "expired-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(accountJson("Ada Lovelace", "110101199001011234", "10.00")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accountNumber").value(matchesPattern(ACCOUNT_NUMBER)));

		assertThat(repository.findAll()).hasSize(1);
		assertThat(idempotencyRecords.findByIdempotencyKey("expired-key")).get()
				.extracting(IdempotencyRecord::getStatus, IdempotencyRecord::getRequestHash)
				.doesNotContain("a".repeat(64));
	}

	@Test
	void rejectsMissingIdempotencyKey() throws Exception {
		mockMvc.perform(post("/accounts")
				.contentType(MediaType.APPLICATION_JSON)
				.content(accountJson("Ada Lovelace", "110101199001011234", "10.00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("missing_idempotency_key"));

		assertThat(repository.findAll()).isEmpty();
		assertThat(idempotencyRecords.findAll()).isEmpty();
	}

	@Test
	void rejectsInvalidIdempotencyKey() throws Exception {
		mockMvc.perform(post("/accounts")
				.header("Idempotency-Key", "not a key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(accountJson("Ada Lovelace", "110101199001011234", "10.00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_idempotency_key"));

		assertThat(repository.findAll()).isEmpty();
	}

	@Test
	void deletesAnExistingAccount() throws Exception {
		String accountNumber = createAccount("delete-ada", "Ada Lovelace", "110101199001011234", "10.00");

		mockMvc.perform(delete("/accounts/{accountNumber}", accountNumber))
				.andExpect(status().isNoContent());

		assertThat(repository.findById(accountNumber)).get()
				.extracting(BankAccount::getStatus)
				.isEqualTo(AccountStatus.CLOSED);
	}

	@Test
	void rejectsDeletingAClosedAccount() throws Exception {
		String accountNumber = createAccount("delete-closed", "Ada Lovelace", "110101199001011234", "10.00");
		mockMvc.perform(delete("/accounts/{accountNumber}", accountNumber))
				.andExpect(status().isNoContent());

		mockMvc.perform(delete("/accounts/{accountNumber}", accountNumber))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("account_closed"))
				.andExpect(jsonPath("$.message").value("Account number " + accountNumber + " is closed"));

		assertThat(repository.findById(accountNumber)).isPresent();
	}

	@Test
	void rejectsDeletingAMissingAccount() throws Exception {
		mockMvc.perform(delete("/accounts/{accountNumber}", "1000000000000001"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("account_not_found"))
				.andExpect(jsonPath("$.message").value("Account number 1000000000000001 does not exist"));
	}

	private String createAccount(String idempotencyKey, String holderName, String holderId, String balance) throws Exception {
		MvcResult result = mockMvc.perform(post("/accounts")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(accountJson(holderName, holderId, balance)))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.accountNumber");
	}

	private String accountJson(String holderName, String holderId, String balance) {
		return """
				{
				  "holderName": "%s",
				  "holderId": "%s",
				  "balance": %s
				}
				""".formatted(holderName, holderId, balance);
	}

}
