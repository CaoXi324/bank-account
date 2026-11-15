package com.hsbc.bankaccount.account.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.hsbc.bankaccount.account.dto.AccountResponse;
import com.hsbc.bankaccount.account.dto.CreateAccountRequest;
import com.hsbc.bankaccount.account.service.CreateAccountService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/accounts")
public class AccountController {

	private final CreateAccountService createAccountService;

	public AccountController(CreateAccountService createAccountService) {
		this.createAccountService = createAccountService;
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

}
