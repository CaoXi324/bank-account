package com.hsbc.bankaccount.account.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.hsbc.bankaccount.account.dto.AccountPageResponse;
import com.hsbc.bankaccount.account.dto.AccountResponse;
import com.hsbc.bankaccount.account.dto.CreateAccountRequest;
import com.hsbc.bankaccount.account.dto.UpdateAccountRequest;
import com.hsbc.bankaccount.account.service.AccountService;
import com.hsbc.bankaccount.account.service.CreateAccountService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/accounts")
public class AccountController {

	private final CreateAccountService createAccountService;

	private final AccountService accountService;

	public AccountController(CreateAccountService createAccountService, AccountService accountService) {
		this.createAccountService = createAccountService;
		this.accountService = accountService;
	}

	@PostMapping
	public ResponseEntity<AccountResponse> create(
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody CreateAccountRequest request) {
		AccountResponse created = createAccountService.create(idempotencyKey, request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{accountNumber}")
				.buildAndExpand(created.accountNumber())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping
	public AccountPageResponse list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return accountService.list(page, size);
	}

	@GetMapping("/{accountNumber}")
	public AccountResponse get(@PathVariable String accountNumber) {
		return accountService.get(accountNumber);
	}

	@PatchMapping("/{accountNumber}")
	public AccountResponse update(@PathVariable String accountNumber, @Valid @RequestBody UpdateAccountRequest request) {
		return accountService.update(accountNumber, request);
	}

	@DeleteMapping("/{accountNumber}")
	public ResponseEntity<Void> delete(@PathVariable String accountNumber) {
		accountService.delete(accountNumber);
		return ResponseEntity.noContent().build();
	}

}
