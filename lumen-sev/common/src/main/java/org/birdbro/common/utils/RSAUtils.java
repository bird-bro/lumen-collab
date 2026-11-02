package org.birdbro.common.utils;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.birdbro.common.core.pojo.PublicKeyMap;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * @author birdbro
 * rsa的加密解密的工具类
 */
@Slf4j
public class RSAUtils {
    /**
     * 算法名称
     */
    private static final String ALGORITHOM = "RSA";
    /**
     * 密钥大小
     */
    private static final int KEY_SIZE = 1024;
    /**
     * 默认的安全服务提供者
     */
    private static final Provider DEFAULT_PROVIDER = new BouncyCastleProvider();

    private static KeyPairGenerator keyPairGen = null;
    /**
     * 缓存的密钥对。
     */
    private static KeyPair oneKeyPair = null;

    static {
        try {
            keyPairGen = KeyPairGenerator.getInstance(ALGORITHOM, DEFAULT_PROVIDER);
        } catch (NoSuchAlgorithmException ex) {
            System.out.println(ex.getMessage());
        }
    }

    private RSAUtils() {
    }

    /**
     * 生成并返回RSA密钥对。
     */
    public static synchronized KeyPair generateKeyPair() {
        try {
            keyPairGen.initialize(KEY_SIZE, new SecureRandom(DateFormatUtils.format(new Date(), "yyyyMMdd").getBytes()));
            oneKeyPair = keyPairGen.generateKeyPair();
            return oneKeyPair;
        } catch (InvalidParameterException ex) {
            log.info("KeyPairGenerator does not support a key length of " + KEY_SIZE + ".");
        } catch (NullPointerException ex) {
            log.info("RSAUtils#KEY_PAIR_GEN is null, can not generate KeyPairGenerator instance.");
        }
        return null;
    }

    /**
     * 使用指定的私钥解密数据。
     *
     * @param privateKey 给定的私钥。
     * @param data       要解密的数据。
     * @return 原数据。
     */
    public static byte[] decrypt(PrivateKey privateKey, byte[] data) throws Exception {
        Cipher ci = Cipher.getInstance(ALGORITHOM, DEFAULT_PROVIDER);
        ci.init(Cipher.DECRYPT_MODE, privateKey);
        return ci.doFinal(data);
    }

    /**
     * 使用默认的私钥解密给定的字符串。
     * <p/>
     * 若{@code encrypttext} 为 {@code null}或空字符串则返回 {@code null}。
     * 私钥不匹配时，返回 {@code null}。
     *
     * @param encrypttext 密文。
     * @return 原文字符串。
     */
    public static String decryptString(String encrypttext, String keyPairStr) {
        if (GadgetUtils.isBlank(encrypttext)) {
            return null;
        }
        try {
            byte[] en_data = Hex.decodeHex(encrypttext.toCharArray());
            byte[] data = decrypt(getPrivateKey(keyPairStr), en_data);
            return new String(data);
        } catch (NullPointerException ex) {
            log.info("keyPair cannot be null.");
        } catch (Exception ex) {
            log.info(String.format("\"%s\" Decryption failed. Cause: %s", encrypttext, ex.getMessage()));
        }
        return null;
    }

    /**
     * 使用默认的私钥解密由JS加密（使用此类提供的公钥加密）的字符串。
     *
     * @param encrypttext 密文。
     * @return {@code encrypttext} 的原文字符串。
     */
    public static String decryptStringByJs(String encrypttext, String keyPairStr) {
        String text = decryptString(encrypttext, keyPairStr);
        if (text == null) {
            return null;
        }
        return GadgetUtils.reverse(text);
    }

    /**
     * 返回已初始化的默认的公钥。
     */
    public static RSAPublicKey getDefaultPublicKey(KeyPair keyPair) {
        if (keyPair != null) {
            return (RSAPublicKey) keyPair.getPublic();
        }
        return null;
    }

    /**
     * 返回已初始化的默认的私钥。
     */
    public static RSAPrivateKey getDefaultPrivateKey(KeyPair keyPair) {
        if (keyPair != null) {
            return (RSAPrivateKey) keyPair.getPrivate();
        }
        return null;
    }

    /**
     * 返回公钥
     *
     * @return
     */
    public static PublicKeyMap getPublicKeyMap(KeyPair keyPair) {
        PublicKeyMap publicKeyMap = new PublicKeyMap();
        RSAPublicKey rsaPublicKey = getDefaultPublicKey(keyPair);
        if (null != rsaPublicKey) {
            publicKeyMap.setOwnModulus(new String(Hex.encodeHex(rsaPublicKey.getModulus().toByteArray())));
            publicKeyMap.setExponent(new String(Hex.encodeHex(rsaPublicKey.getPublicExponent().toByteArray())));
        }
        return publicKeyMap;
    }

    public static RSAPrivateKey getPrivateKey(String key) throws Exception {
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
        return privateKey;
    }


}
