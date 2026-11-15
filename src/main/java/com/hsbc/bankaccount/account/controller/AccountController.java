package com.hsbc.bankaccount.account.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.hsbc.bankaccount.account.dto.AccountResponse;
import com.hsbc.bankaccount.account.dto.CreateAccountRequest;
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

	@DeleteMapping("/{accountNumber}")
	public ResponseEntity<Void> delete(@PathVariable String accountNumber) {
		accountService.delete(accountNumber);
		return ResponseEntity.noContent().build();
	}

}
