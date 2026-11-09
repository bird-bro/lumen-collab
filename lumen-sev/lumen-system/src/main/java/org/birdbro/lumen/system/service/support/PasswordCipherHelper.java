package org.birdbro.lumen.system.service.support;

import java.math.BigInteger;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.SM2;

import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.math.ec.ECPoint;

import org.birdbro.common.enums.ExceptionEnum;
import org.birdbro.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SM2 password-transport cipher (password-transport-encryption-api task 1.5, coordination
 * password-transport-encryption D2/D3/D4): the ONE place a password ciphertext is turned
 * back into the raw value before the untouched BCrypt lines, and the source of the
 * derived public key that {@code GET /api/v1/system/auth-key} distributes.
 *
 * <p>Config (coordination D2): {@code lumen.security.password-crypto.key-id} +
 * {@code lumen.security.password-crypto.sm2-private-key} - the latter is the 32-byte raw
 * scalar D as 64-char hex, a format Hutool and sm-crypto share verbatim. The public key
 * is NEVER configured: Hutool derives it from D (single source of truth, no pair drift).
 * The private key is sensitive config (D8) - it ships via env vars / the gitignored
 * application-dev.yml, never a committed file.</p>
 *
 * <p>Wire format (coordination D4, refined during apply): the ciphertext is SM2 C1C3C2,
 * hex, in the STANDARD uncompressed-point encoding - {@code "04" || C1 || C3 || C2}.
 * The frontend (sm-crypto {@code doEncrypt(..., 1)}) emits a MARKERLESS C1, so the SPA
 * prepends {@code "04"} before sending; the fixed interop vector in
 * {@code PasswordCipherHelperTest} (sm-crypto-produced, Java-decrypted) pins this exact
 * contract. Anything not decryptable under the configured key is a request-parameter
 * failure: {@code A0400} (the copied {@code ExceptionEnum} constant REUSED verbatim -
 * NOT A0210, so a transport-format failure can never corrupt wrong-password counters,
 * and it carries no account-existence signal because it fires BEFORE the account
 * lookup).</p>
 *
 * <p>Fail-fast (design D2): a blank or malformed private key throws
 * {@link IllegalStateException} at bean construction - a service that cannot decrypt
 * passwords must not boot half-working.</p>
 *
 * @author birdbro
 */
@Component
public class PasswordCipherHelper {

    /** SM2 raw scalar D length in bytes - a 64-char hex string. */
    private static final int SCALAR_BYTES = 32;

    private final String keyId;
    private final SM2 sm2;
    private final String publicKeyHex;

    public PasswordCipherHelper(
            @Value("${lumen.security.password-crypto.key-id:}") String keyId,
            @Value("${lumen.security.password-crypto.sm2-private-key:}") String privateKeyHex) {
        if (StrUtil.isBlank(keyId)) {
            throw missingConfig("lumen.security.password-crypto.key-id");
        }
        if (StrUtil.isBlank(privateKeyHex)) {
            throw missingConfig("lumen.security.password-crypto.sm2-private-key");
        }
        String normalized = privateKeyHex.trim().toLowerCase();
        if (!normalized.matches("[0-9a-f]+")) {
            throw new IllegalStateException(
                    "lumen.security.password-crypto.sm2-private-key must be hex, but the "
                            + "configured value contains non-hex characters");
        }
        if (normalized.length() != SCALAR_BYTES * 2) {
            throw new IllegalStateException(
                    "lumen.security.password-crypto.sm2-private-key must be the 32-byte raw "
                            + "scalar D as " + (SCALAR_BYTES * 2) + " hex chars, but the configured "
                            + "value has " + normalized.length() + " chars");
        }
        try {
            // The public key is DERIVED here, never configured (coordination D2): Hutool
            // constructed private-only can decrypt but returns a null public key, so the
            // point comes from the BC primitive directly - Q = D*G on the standard curve.
            X9ECParameters curve = GMNamedCurves.getByName("sm2p256v1");
            BigInteger d = new BigInteger(1, HexUtil.decodeHex(normalized));
            ECPoint q = curve.getG().multiply(d).normalize();
            if (q.isInfinity()) {
                throw new IllegalStateException(
                        "lumen.security.password-crypto.sm2-private-key is not a usable SM2 "
                                + "scalar: D*G is the point at infinity");
            }
            this.publicKeyHex = HexUtil.encodeHexStr(q.getEncoded(false)).toLowerCase();
            // Hutool built from BOTH key strings: its engine runs on the key params, but
            // its getPublicKey() field stays null even then (jshell-probed 2026-11-09) -
            // the derived point above is the single source for /auth-key.
            this.sm2 = SmUtil.sm2(normalized, this.publicKeyHex);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "lumen.security.password-crypto.sm2-private-key is not a usable SM2 "
                            + "scalar: " + e.getMessage(), e);
        }
        this.keyId = keyId;
    }

    /**
     * Decrypts a wire-format password ciphertext ({@code 04||C1||C3||C2}, hex) into the
     * raw password. Any failure shape - blank, non-hex, odd length, tampered, foreign
     * key - throws the SAME {@code A0400} business error with a refresh-hinting msg;
     * the caller treats the raw value exactly like the request's former plaintext field.
     *
     * @param cipherHex the request's password field (SM2 ciphertext, standard point encoding)
     * @return the decrypted raw password
     */
    public String decrypt(String cipherHex) {
        if (StrUtil.isBlank(cipherHex)) {
            throw decryptFailed("empty ciphertext");
        }
        String normalized = cipherHex.trim().toLowerCase();
        if (!normalized.matches("[0-9a-f]+") || normalized.length() % 2 != 0) {
            throw decryptFailed("value is not hex ciphertext");
        }
        try {
            String raw = this.sm2.decryptStr(normalized, KeyType.PrivateKey);
            if (StrUtil.isEmpty(raw)) {
                throw decryptFailed("ciphertext decrypted to an empty value");
            }
            return raw;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            // Wrong key, tampered bytes, truncated C1/C3 - one uniform rejection, never
            // a stack-trace 500 (GlobalExceptionHandler renders BusinessException as the
            // A-class envelope; a raw engine exception would surface as B0001).
            throw decryptFailed("ciphertext does not decrypt under the configured key");
        }
    }

    /** The configured key id (rotation hook; exposed by the auth-key endpoint). */
    public String getKeyId() {
        return this.keyId;
    }

    /**
     * The derived public key as the 04-prefixed 130-char hex uncompressed point - the
     * exact format the frontend feeds to {@code sm2.doEncrypt} (after prepending the
     * marker to its own markerless output). BC point encoding (04||x||y, 65 bytes), NOT
     * the X.509 SPKI - sm-crypto consumes the raw point hex verbatim (coordination
     * D3/D4).
     */
    public String getPublicKeyHex() {
        return this.publicKeyHex;
    }

    private static IllegalStateException missingConfig(String property) {
        return new IllegalStateException("lumen-system cannot start: " + property
                + " is not configured. Password endpoints REQUIRE the SM2 transport key "
                + "(coordination password-transport-encryption D2): generate a keypair for "
                + "this environment and provide the 32-byte scalar D hex via an env var or "
                + "the gitignored application-dev.yml - never commit it.");
    }

    private static BusinessException decryptFailed(String detail) {
        return new BusinessException(ExceptionEnum.ERROR_A0400,
                "密码解密失败，请刷新页面后重试 (" + detail + ")");
    }
}
