/*******************************************************************************
 * Copyright (c) 2015 Sebastian Stenzel and others.
 * This file is licensed under the terms of the MIT license.
 * See the LICENSE.txt file for more info.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.crypto.engine.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.BaseNCodec;
import org.cryptomator.crypto.engine.AuthenticationFailedException;
import org.cryptomator.crypto.engine.FilenameCryptor;
import org.cryptomator.siv.SivMode;

class FilenameCryptorImpl implements FilenameCryptor {

	private static final BaseNCodec BASE32 = new Base32();
	private static final ThreadLocal<MessageDigest> SHA1 = new ThreadLocalSha1();
	private static final SivMode AES_SIV = new SivMode();

	private final SecretKey encryptionKey;
	private final SecretKey macKey;

	FilenameCryptorImpl(SecretKey encryptionKey, SecretKey macKey) {
		this.encryptionKey = encryptionKey;
		this.macKey = macKey;
	}

	@Override
	public String hashDirectoryId(String cleartextDirectoryId) {
		final byte[] cleartextBytes = cleartextDirectoryId.getBytes(StandardCharsets.UTF_8);
		byte[] encryptedBytes = AES_SIV.encrypt(encryptionKey, macKey, cleartextBytes);
		final byte[] hashedBytes = SHA1.get().digest(encryptedBytes);
		return BASE32.encodeAsString(hashedBytes);
	}

	@Override
	public String encryptFilename(String cleartextName) {
		final byte[] cleartextBytes = cleartextName.getBytes(StandardCharsets.UTF_8);
		final byte[] encryptedBytes = AES_SIV.encrypt(encryptionKey, macKey, cleartextBytes);
		return BASE32.encodeAsString(encryptedBytes);
	}

	@Override
	public String decryptFilename(String ciphertextName) {
		final byte[] encryptedBytes = BASE32.decode(ciphertextName);
		try {
			final byte[] cleartextBytes = AES_SIV.decrypt(encryptionKey, macKey, encryptedBytes);
			return new String(cleartextBytes, StandardCharsets.UTF_8);
		} catch (AEADBadTagException e) {
			throw new AuthenticationFailedException("Authentication failed.", e);
		}
	}

	private static class ThreadLocalSha1 extends ThreadLocal<MessageDigest> {

		@Override
		protected MessageDigest initialValue() {
			try {
				return MessageDigest.getInstance("SHA-1");
			} catch (NoSuchAlgorithmException e) {
				throw new AssertionError("SHA-1 exists in every JVM");
			}
		}

		@Override
		public MessageDigest get() {
			final MessageDigest messageDigest = super.get();
			messageDigest.reset();
			return messageDigest;
		}
	}

}
