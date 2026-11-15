package com.hsbc.bankaccount.account.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.hsbc.bankaccount.account.entity.BankAccount;

public record AccountPageResponse(
		List<AccountResponse> accounts,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public static AccountPageResponse from(Page<BankAccount> result) {
		return new AccountPageResponse(
				result.getContent().stream().map(AccountResponse::from).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}

}
