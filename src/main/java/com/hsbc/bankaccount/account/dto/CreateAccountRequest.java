package com.hsbc.bankaccount.account.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
		@NotBlank @Size(max = 100) String holderName,
		@NotBlank @Pattern(regexp = "^[1-9]\\d{16}[\\dXx]$", message = "must be an 18-digit ID card number") String holderId,
		@NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal balance) {
}
