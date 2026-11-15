package com.hsbc.bankaccount.account.service;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.hsbc.bankaccount.account.dto.CreateAccountRequest;

public final class AccountRequestHash {

	private AccountRequestHash() {
	}

	public static String of(CreateAccountRequest request) {
		String canonical = request.holderName()
				+ "\n"
				+ request.idCardNumber()
				+ "\n"
				+ request.phoneNumber()
				+ "\n"
				+ request.balance().setScale(2, RoundingMode.HALF_UP).toPlainString();
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

}
