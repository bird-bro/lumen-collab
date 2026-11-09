package org.birdbro.lumen.system.service.support;

import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.SM2;

import org.birdbro.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SM2 password-cipher helper unit proofs (password-transport-encryption-api tasks 1.3,
 * design T1/T2 - coordination D4). PLAIN JUnit, no Spring: the helper is built from the
 * same fixed test scalar the integration suites ship in application-test.yml.
 *
 * <p>The interop fixed vector is the HEART of this suite: a ciphertext produced ONCE by
 * the FRONTEND library (sm-crypto 0.3.13, {@code doEncrypt("Test@12345", pub, 1)} =
 * C1C3C2, markerless C1) must decrypt byte-exact on the Java side forever. The wire
 * format is the standard uncompressed-point encoding: {@code "04" + sm-crypto output} -
 * the marker the frontend prepends (coordination D4 as refined during apply: BC's
 * SM2Engine requires the 04 point header; sm-crypto strips it in its native output).
 * Any Hutool/sm-crypto version bump that flips mode or encoding turns THIS test red,
 * never a production login.</p>
 *
 * @author birdbro
 */
class PasswordCipherHelperTest {

    /** The fixed test scalar D - identical to application-test.yml (task 1.2). */
    private static final String TEST_PRIVATE_D =
            "56d66802243a734aba690c6f2553ca69c87e8e78f9b282a19686d7fe1683cd83";

    /** The derived public point of TEST_PRIVATE_D (04||x||y, 130 hex chars). */
    private static final String TEST_PUBLIC_POINT =
            "0440025ec765361018ae5c5e603f9e55354e5aa2e1fcab3d541d7e6f560b40039f7d"
                    + "a1972abc804eb41b0332bb04f348b3baa7009091d150c4a77763efdfd8d553";

    /** sm-crypto 0.3.13 C1C3C2 output for "Test@12345" under TEST_PUBLIC_POINT (markerless). */
    private static final String SM_CRYPTO_VECTOR =
            "9d84ff0341ff1c0dcd95fcfb8ca1ce8025a60a7290d3f3dd4ab67b6f61b880baa"
                    + "4b0d6a1cb006873964ec2b196ff4518be41561a4001edfb9c84a6e6a117b601"
                    + "c0a3de3b85516bf0385fd95582a3bc16939fab0249f2927bd7e44c4b3c667b8"
                    + "2c2dc36c530eafa3120fb";

    /** The wire format: standard point marker + the sm-crypto native hex. */
    private static final String WIRE_VECTOR = "04" + SM_CRYPTO_VECTOR;

    private static final String VECTOR_PLAINTEXT = "Test@12345";

    private PasswordCipherHelper helper() {
        return new PasswordCipherHelper("sm2-test", TEST_PRIVATE_D);
    }

    @Test
    void interopFixedVectorDecryptsExactPlaintext() {
        assertEquals(VECTOR_PLAINTEXT, helper().decrypt(WIRE_VECTOR),
                "the sm-crypto-produced vector must decrypt byte-exact - the cross-library "
                        + "C1C3C2-hex pin (coordination D4)");
    }

    @Test
    void derivedPublicKeyIsTheFixedTestPoint() {
        assertEquals(TEST_PUBLIC_POINT, helper().getPublicKeyHex(),
                "the public key must derive from D via D*G as the 04-prefixed 130-char hex point");
        assertEquals(130, helper().getPublicKeyHex().length());
        assertTrue(helper().getPublicKeyHex().startsWith("04"));
    }

    @Test
    void keyIdIsExposedFromConfig() {
        assertEquals("sm2-test", helper().getKeyId());
    }

    @Test
    void helperSideRoundTripMatchesTheInteropContract() {
        // The Java-side mirror of the frontend flow: encrypt with the derived public
        // point (Hutool emits the 04 marker itself), decrypt through the helper.
        SM2 encryptor = SmUtil.sm2((String) null, TEST_PUBLIC_POINT);
        String wire = encryptor.encryptHex(VECTOR_PLAINTEXT, KeyType.PublicKey).toLowerCase();
        assertNotEquals(VECTOR_PLAINTEXT, wire);
        assertEquals(VECTOR_PLAINTEXT, helper().decrypt(wire),
                "a Hutool-produced ciphertext (04 marker included) decrypts identically - "
                        + "the wire contract accepts the standard point encoding");
    }

    @Test
    void blankCiphertextIsA0400() {
        BusinessException e = assertThrows(BusinessException.class, () -> helper().decrypt(""));
        assertEquals("A0400", e.getCode());
        BusinessException nullCase = assertThrows(BusinessException.class,
                () -> helper().decrypt(null));
        assertEquals("A0400", nullCase.getCode());
    }

    @Test
    void nonHexCiphertextIsA0400() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> helper().decrypt("this-is-plaintext-admin123"));
        assertEquals("A0400", e.getCode());
    }

    @Test
    void oddLengthHexIsA0400() {
        BusinessException e = assertThrows(BusinessException.class, () -> helper().decrypt("abc"));
        assertEquals("A0400", e.getCode());
    }

    @Test
    void markerlessCiphertextIsA0400() {
        // sm-crypto NATIVE output lacks the 04 marker - the wire format is the MARKERED
        // form (frontend prepends). A markerless body must not accidentally decrypt.
        BusinessException e = assertThrows(BusinessException.class,
                () -> helper().decrypt(SM_CRYPTO_VECTOR));
        assertEquals("A0400", e.getCode());
    }

    @Test
    void tamperedCiphertextIsA0400() {
        char[] chars = WIRE_VECTOR.toCharArray();
        chars[chars.length - 1] = chars[chars.length - 1] == '0' ? '1' : '0';
        BusinessException e = assertThrows(BusinessException.class,
                () -> helper().decrypt(new String(chars)));
        assertEquals("A0400", e.getCode());
    }

    @Test
    void foreignKeyCiphertextIsA0400() {
        // Encrypted under a DIFFERENT keypair entirely - decrypting with the configured
        // key must fail closed (the stale-rotation scenario). Hutool needs BOTH key
        // strings for encryption (its getPublicKey() field stays null even when set).
        SM2 stranger = SmUtil.sm2(
                "5b8cab42868d9506465adf66d61cd8c4ecfb926a413b2d111572790e0e868d07",
                "0453f6a3ca134b1db5c97deccba6b7a314f76837fade4ed0491bede399b5364526387"
                        + "b177dbad3475b8becbc056f5e305aad3bd4d1a00fc9d949c04cdf0480b372");
        String foreign = stranger.encryptHex(VECTOR_PLAINTEXT, KeyType.PublicKey).toLowerCase();
        BusinessException e = assertThrows(BusinessException.class, () -> helper().decrypt(foreign));
        assertEquals("A0400", e.getCode());
    }
}
