package com.hsbc.bankaccount.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
import com.hsbc.bankaccount.account.repository.TransferRepository;
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

	@Autowired
	private TransferRepository transfers;

	@AfterEach
	void deleteSavedRows() {
		transfers.deleteAll();
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
				.andExpect(jsonPath("$.contactInfo").value("ada@example.com"))
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
				.andExpect(jsonPath("$.fields.contactInfo").exists())
				.andExpect(jsonPath("$.fields.balance").exists())
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
		CreateAccountRequest request = new CreateAccountRequest("Ada Lovelace", "110101199001011234", "ada@example.com", new BigDecimal("10.00"));
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
	void returnsAccountDetails() throws Exception {
		String accountNumber = createAccount("get-ada", "Ada Lovelace", "110101199001011234", "10.00");

		mockMvc.perform(get("/accounts/{accountNumber}", accountNumber))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountNumber").value(accountNumber))
				.andExpect(jsonPath("$.holderName").value("Ada Lovelace"))
				.andExpect(jsonPath("$.holderId").value("110101199001011234"))
				.andExpect(jsonPath("$.contactInfo").value("ada@example.com"))
				.andExpect(jsonPath("$.balance").value(10.00))
				.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void returnsClosedAccountDetails() throws Exception {
		String accountNumber = createAccount("get-closed", "Ada Lovelace", "110101199001011234", "10.00");
		mockMvc.perform(delete("/accounts/{accountNumber}", accountNumber))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/accounts/{accountNumber}", accountNumber))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountNumber").value(accountNumber))
				.andExpect(jsonPath("$.status").value("CLOSED"));
	}

	@Test
	void rejectsAMissingAccount() throws Exception {
		mockMvc.perform(get("/accounts/{accountNumber}", "1000000000000001"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("account_not_found"))
				.andExpect(jsonPath("$.message").value("Account number 1000000000000001 does not exist"));
	}

	@Test
	void listsAccountsByPage() throws Exception {
		String first = createAccount("list-1", "Ada Lovelace", "110101199001011234", "10.00");
		String second = createAccount("list-2", "Grace Hopper", "110101199002021234", "20.00");
		String third = createAccount("list-3", "Katherine Johnson", "110101199003031234", "30.00");

		MvcResult firstPage = mockMvc.perform(get("/accounts").param("page", "0").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accounts", hasSize(2)))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andReturn();
		MvcResult secondPage = mockMvc.perform(get("/accounts").param("page", "1").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accounts", hasSize(1)))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andReturn();

		String firstPageBody = firstPage.getResponse().getContentAsString();
		String secondPageBody = secondPage.getResponse().getContentAsString();
		assertThat(JsonPath.<String>read(firstPageBody, "$.accounts[0].accountNumber"))
				.isLessThan(JsonPath.<String>read(firstPageBody, "$.accounts[1].accountNumber"));
		assertThat(List.of(
				JsonPath.<String>read(firstPageBody, "$.accounts[0].accountNumber"),
				JsonPath.<String>read(firstPageBody, "$.accounts[1].accountNumber"),
				JsonPath.<String>read(secondPageBody, "$.accounts[0].accountNumber")))
				.containsExactlyInAnyOrder(first, second, third);
	}

	@Test
	void listsAClosedAccount() throws Exception {
		String accountNumber = createAccount("list-closed", "Ada Lovelace", "110101199001011234", "10.00");
		mockMvc.perform(delete("/accounts/{accountNumber}", accountNumber))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/accounts").param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accounts", hasSize(1)))
				.andExpect(jsonPath("$.accounts[0].accountNumber").value(accountNumber))
				.andExpect(jsonPath("$.accounts[0].status").value("CLOSED"));
	}

	@Test
	void rejectsAnInvalidPageRequest() throws Exception {
		mockMvc.perform(get("/accounts").param("page", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.message").value("page must be 0 or greater and size must be between 1 and 100"));

		mockMvc.perform(get("/accounts").param("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_failed"));
	}

	@Test
	void updatesTheHolderNameAndContactInfo() throws Exception {
		String accountNumber = createAccount("update-ada", "Ada Lovelace", "110101199001011234", "10.00");

		mockMvc.perform(patch("/accounts/{accountNumber}", accountNumber)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"holderName\":\"Augusta Ada King\",\"contactInfo\":\"king@example.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountNumber").value(accountNumber))
				.andExpect(jsonPath("$.holderName").value("Augusta Ada King"))
				.andExpect(jsonPath("$.contactInfo").value("king@example.com"))
				.andExpect(jsonPath("$.holderId").value("110101199001011234"))
				.andExpect(jsonPath("$.balance").value(10.00))
				.andExpect(jsonPath("$.status").value("OPEN"));

		assertThat(repository.findById(accountNumber)).get()
				.extracting(BankAccount::getHolderName, BankAccount::getContactInfo)
				.containsExactly("Augusta Ada King", "king@example.com");
	}

	@Test
	void rejectsAnEmptyUpdateRequest() throws Exception {
		String accountNumber = createAccount("update-empty", "Ada Lovelace", "110101199001011234", "10.00");

		mockMvc.perform(patch("/accounts/{accountNumber}", accountNumber)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("EMPTY_UPDATE_REQUEST"));

		assertThat(repository.findById(accountNumber)).get()
				.extracting(BankAccount::getHolderName, BankAccount::getContactInfo)
				.containsExactly("Ada Lovelace", "ada@example.com");
	}

	@Test
	void rejectsInvalidHolderName() throws Exception {
		String accountNumber = createAccount("update-invalid-name", "Ada Lovelace", "110101199001011234", "10.00");

		mockMvc.perform(patch("/accounts/{accountNumber}", accountNumber)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"holderName\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_ACCOUNT_HOLDER_NAME"));

		assertThat(repository.findById(accountNumber)).get()
				.extracting(BankAccount::getHolderName, BankAccount::getContactInfo)
				.containsExactly("Ada Lovelace", "ada@example.com");
	}

	@Test
	void rejectsInvalidContactInfo() throws Exception {
		String accountNumber = createAccount("update-invalid", "Ada Lovelace", "110101199001011234", "10.00");

		mockMvc.perform(patch("/accounts/{accountNumber}", accountNumber)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"holderName\":\"Augusta Ada King\",\"contactInfo\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_CONTACT_INFO"));

		assertThat(repository.findById(accountNumber)).get()
				.extracting(BankAccount::getHolderName, BankAccount::getContactInfo)
				.containsExactly("Ada Lovelace", "ada@example.com");
	}

	@Test
	void rejectsUpdatingAClosedAccount() throws Exception {
		String accountNumber = createAccount("update-closed", "Ada Lovelace", "110101199001011234", "10.00");
		mockMvc.perform(delete("/accounts/{accountNumber}", accountNumber))
				.andExpect(status().isNoContent());

		mockMvc.perform(patch("/accounts/{accountNumber}", accountNumber)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"holderName\":\"Augusta Ada King\",\"contactInfo\":\"king@example.com\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("account_closed"))
				.andExpect(jsonPath("$.message").value("Account number " + accountNumber + " is closed"));

		assertThat(repository.findById(accountNumber)).get()
				.extracting(BankAccount::getHolderName)
				.isEqualTo("Ada Lovelace");
	}

	@Test
	void rejectsUpdatingAMissingAccount() throws Exception {
		mockMvc.perform(patch("/accounts/{accountNumber}", "1000000000000001")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"holderName\":\"Augusta Ada King\",\"contactInfo\":\"king@example.com\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("account_not_found"))
				.andExpect(jsonPath("$.message").value("Account number 1000000000000001 does not exist"));
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

	@Test
	void transfersFundsBetweenAccounts() throws Exception {
		String from = createAccount("transfer-from", "Ada Lovelace", "110101199001011234", "10.00");
		String to = createAccount("transfer-to", "Grace Hopper", "110101199002021234", "5.00");

		mockMvc.perform(post("/accounts/transfers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(transferJson(from, to, "4.50")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.from.accountNumber").value(from))
				.andExpect(jsonPath("$.from.balance").value(5.50))
				.andExpect(jsonPath("$.to.accountNumber").value(to))
				.andExpect(jsonPath("$.to.balance").value(9.50))
				.andExpect(jsonPath("$.amount").value(4.50))
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.createdAt").exists());

		assertThat(transfers.findAll()).singleElement().satisfies(transfer -> {
			assertThat(transfer.getFromAccountNumber()).isEqualTo(from);
			assertThat(transfer.getToAccountNumber()).isEqualTo(to);
			assertThat(transfer.getAmount()).isEqualByComparingTo("4.50");
			assertThat(transfer.getCreatedAt()).isNotNull();
		});

		assertThat(repository.findById(from)).get()
				.extracting(BankAccount::getBalance)
				.usingComparator(BigDecimal::compareTo)
				.isEqualTo(new BigDecimal("5.50"));
		assertThat(repository.findById(to)).get()
				.extracting(BankAccount::getBalance)
				.usingComparator(BigDecimal::compareTo)
				.isEqualTo(new BigDecimal("9.50"));
	}

	@Test
	void rejectsATransferWithInsufficientFunds() throws Exception {
		String from = createAccount("transfer-poor", "Ada Lovelace", "110101199001011234", "10.00");
		String to = createAccount("transfer-rich", "Grace Hopper", "110101199002021234", "5.00");

		mockMvc.perform(post("/accounts/transfers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(transferJson(from, to, "10.01")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("insufficient_funds"))
				.andExpect(jsonPath("$.message").value("Account number " + from + " has insufficient funds"));

		assertThat(transfers.findAll()).isEmpty();

		assertThat(repository.findById(from)).get()
				.extracting(BankAccount::getBalance)
				.usingComparator(BigDecimal::compareTo)
				.isEqualTo(new BigDecimal("10.00"));
		assertThat(repository.findById(to)).get()
				.extracting(BankAccount::getBalance)
				.usingComparator(BigDecimal::compareTo)
				.isEqualTo(new BigDecimal("5.00"));
	}

	@Test
	void rejectsATransferToTheSameAccount() throws Exception {
		String accountNumber = createAccount("transfer-same", "Ada Lovelace", "110101199001011234", "10.00");

		mockMvc.perform(post("/accounts/transfers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(transferJson(accountNumber, accountNumber, "1.00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("same_account"));

		assertThat(repository.findById(accountNumber)).get()
				.extracting(BankAccount::getBalance)
				.usingComparator(BigDecimal::compareTo)
				.isEqualTo(new BigDecimal("10.00"));
	}

	@Test
	void rejectsATransferWhenAnAccountIsMissing() throws Exception {
		String from = createAccount("transfer-missing-from", "Ada Lovelace", "110101199001011234", "10.00");

		mockMvc.perform(post("/accounts/transfers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(transferJson(from, "1000000000000001", "1.00")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("account_not_found"))
				.andExpect(jsonPath("$.message").value("Account number 1000000000000001 does not exist"));

		assertThat(repository.findById(from)).get()
				.extracting(BankAccount::getBalance)
				.usingComparator(BigDecimal::compareTo)
				.isEqualTo(new BigDecimal("10.00"));
	}

	@Test
	void rejectsATransferWhenAnAccountIsClosed() throws Exception {
		String from = createAccount("transfer-closed-from", "Ada Lovelace", "110101199001011234", "10.00");
		String to = createAccount("transfer-closed-to", "Grace Hopper", "110101199002021234", "5.00");
		mockMvc.perform(delete("/accounts/{accountNumber}", from))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/accounts/transfers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(transferJson(from, to, "1.00")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("account_closed"))
				.andExpect(jsonPath("$.message").value("Account number " + from + " is closed"));

		assertThat(repository.findById(to)).get()
				.extracting(BankAccount::getBalance)
				.usingComparator(BigDecimal::compareTo)
				.isEqualTo(new BigDecimal("5.00"));
	}

	@Test
	void rejectsANonPositiveTransferAmount() throws Exception {
		mockMvc.perform(post("/accounts/transfers")
				.contentType(MediaType.APPLICATION_JSON)
				.content(transferJson("1000000000000001", "1000000000000002", "0")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("validation_failed"))
				.andExpect(jsonPath("$.fields.amount").exists());
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
				  "contactInfo": "ada@example.com",
				  "balance": %s
				}
				""".formatted(holderName, holderId, balance);
	}

	private String transferJson(String fromAccountNumber, String toAccountNumber, String amount) {
		return """
				{
				  "fromAccountNumber": "%s",
				  "toAccountNumber": "%s",
				  "amount": %s
				}
				""".formatted(fromAccountNumber, toAccountNumber, amount);
	}

}
