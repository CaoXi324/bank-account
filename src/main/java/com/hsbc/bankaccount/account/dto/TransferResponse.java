package com.hsbc.bankaccount.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferResponse(
		Long id,
		AccountResponse from,
		AccountResponse to,
		BigDecimal amount,
		Instant createdAt) {
}
