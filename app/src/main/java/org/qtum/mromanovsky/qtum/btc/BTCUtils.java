package org.qtum.mromanovsky.qtum.btc;

import org.spongycastle.asn1.ASN1InputStream;
import org.spongycastle.asn1.ASN1Integer;
import org.spongycastle.asn1.DERSequenceGenerator;
import org.spongycastle.asn1.DLSequence;
import org.spongycastle.asn1.sec.SECNamedCurves;
import org.spongycastle.asn1.x9.X9ECParameters;
import org.spongycastle.crypto.digests.RIPEMD160Digest;
import org.spongycastle.crypto.engines.AESEngine;
import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.crypto.params.ParametersWithRandom;
import org.spongycastle.crypto.signers.ECDSASigner;
import org.spongycastle.math.ec.ECPoint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

@SuppressWarnings({"WeakerAccess", "TryWithIdenticalCatches", "unused"})
public final class BTCUtils {
    private static final ECDomainParameters EC_PARAMS;
    private static final char[] BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    public static final TrulySecureRandom SECURE_RANDOM = new TrulySecureRandom();
    private static final BigInteger LARGEST_PRIVATE_KEY = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
    public static final long MIN_FEE_PER_KB = 10;
    public static final long MAX_ALLOWED_FEE = BTCUtils.parseValue("0.1");
    public static final long MIN_PRIORITY_FOR_NO_FEE = 57600000;
    public static final long MIN_MIN_OUTPUT_VALUE_FOR_NO_FEE = 10000000L;
    public static final int MAX_TX_LEN_FOR_NO_FEE = 10000;
    public static final float EXPECTED_BLOCKS_PER_DAY = 144.0f;//(expected confirmations per day)

    static {
        X9ECParameters params = SECNamedCurves.getByName("secp256k1");
        EC_PARAMS = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
    }

    public static byte[] generatePublicKey(BigInteger privateKey, boolean compressed) {
        synchronized (EC_PARAMS) {
            ECPoint uncompressed = EC_PARAMS.getG().multiply(privateKey);
            return uncompressed.getEncoded(compressed);
        }
    }

    public static byte[] doubleSha256(byte[] bytes) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return sha256.digest(sha256.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String formatValue(double value) {
        if (value < 0) {
            throw new NumberFormatException("Negative value " + value);
        }
        String s = String.format(Locale.US, "%.8f", value);
        while (s.length() > 1 && (s.endsWith("0") || s.endsWith("."))) {
            s = (s.substring(0, s.length() - 1));
        }
        return s;
    }

    public static String formatValue(long value) throws NumberFormatException {
        if (value < 0) {
            throw new NumberFormatException("Negative value " + value);
        }
        StringBuilder sb = new StringBuilder(Long.toString(value));
        while (sb.length() <= 8) {
            sb.insert(0, '0');
        }
        sb.insert(sb.length() - 8, '.');
        while (sb.length() > 1 && (sb.charAt(sb.length() - 1) == '0' || sb.charAt(sb.length() - 1) == '.')) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    public static long parseValue(String valueStr) throws NumberFormatException {
        return (long) (Double.parseDouble(valueStr) * 1e8);
    }

    public static class PrivateKeyInfo {
        public static final int TYPE_WIF = 0;
        public static final int TYPE_MINI = 1;
        public static final int TYPE_BRAIN_WALLET = 2;
        public final int type;
        public final String privateKeyEncoded;
        public final BigInteger privateKeyDecoded;
        public final boolean isPublicKeyCompressed;

        public PrivateKeyInfo(int type, String privateKeyEncoded, BigInteger privateKeyDecoded, boolean isPublicKeyCompressed) {
            this.type = type;
            this.privateKeyEncoded = privateKeyEncoded;
            this.privateKeyDecoded = privateKeyDecoded;
            this.isPublicKeyCompressed = isPublicKeyCompressed;
        }
    }

    public static class Bip38PrivateKeyInfo extends PrivateKeyInfo {
        public static final int TYPE_BIP38 = 4;

        public final String confirmationCode;
        public final String password;

        public Bip38PrivateKeyInfo(String privateKeyEncoded, String confirmationCode, boolean isPublicKeyCompressed) {
            super(TYPE_BIP38, privateKeyEncoded, null, isPublicKeyCompressed);
            this.confirmationCode = confirmationCode;
            this.password = null;
        }

        public Bip38PrivateKeyInfo(String privateKeyEncoded, BigInteger privateKeyDecoded, String password, boolean isPublicKeyCompressed) {
            super(TYPE_BIP38, privateKeyEncoded, privateKeyDecoded, isPublicKeyCompressed);
            this.confirmationCode = null;
            this.password = password;
        }
    }

    /**
     * Decodes given string as private key
     *
     * @param encodedPrivateKey a text what is likely a private key
     * @return decoded private key and its information
     */
    public static PrivateKeyInfo decodePrivateKey(String encodedPrivateKey) {
        if (encodedPrivateKey.length() > 0) {
            try {
                byte[] decoded = decodeBase58(encodedPrivateKey);
                if (decoded != null && (decoded.length == 37 || decoded.length == 38) && (decoded[0] & 0xff) == 0x80) {
                    if (verifyChecksum(decoded)) {
                        byte[] secret = new byte[32];
                        System.arraycopy(decoded, 1, secret, 0, secret.length);
                        boolean isPublicKeyCompressed;
                        if (decoded.length == 38) {
                            if (decoded[decoded.length - 5] == 1) {
                                isPublicKeyCompressed = true;
                            } else {
                                return null;
                            }
                        } else {
                            isPublicKeyCompressed = false;
                        }
                        BigInteger privateKeyBigInteger = new BigInteger(1, secret);
                        if (privateKeyBigInteger.compareTo(BigInteger.ONE) > 0 && privateKeyBigInteger.compareTo(LARGEST_PRIVATE_KEY) < 0) {
                            return new PrivateKeyInfo(PrivateKeyInfo.TYPE_WIF, encodedPrivateKey, privateKeyBigInteger, isPublicKeyCompressed);
                        }
                    }
                } else if (decoded != null && decoded.length == 43 && (decoded[0] & 0xff) == 0x01 && ((decoded[1] & 0xff) == 0x43 || (decoded[1] & 0xff) == 0x42)) {
                    if (verifyChecksum(decoded)) {
                        return new PrivateKeyInfo(Bip38PrivateKeyInfo.TYPE_BIP38, encodedPrivateKey, null, false);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return decodePrivateKeyAsSHA256(encodedPrivateKey);
    }

    /**
     * Decodes brainwallet and mini keys. Both are SHA256(input), but mini keys have basic checksum verification.
     *
     * @param encodedPrivateKey input
     * @return private key what is SHA256 of the input string
     */
    public static PrivateKeyInfo decodePrivateKeyAsSHA256(String encodedPrivateKey) {
        if (encodedPrivateKey.length() > 0) {
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                BigInteger privateKeyBigInteger = new BigInteger(1, sha256.digest(encodedPrivateKey.getBytes()));
                if (privateKeyBigInteger.compareTo(BigInteger.ONE) > 0 && privateKeyBigInteger.compareTo(LARGEST_PRIVATE_KEY) < 0) {
                    int type;

                    if (sha256.digest((encodedPrivateKey + '?').getBytes("UTF-8"))[0] == 0) {
                        type = PrivateKeyInfo.TYPE_MINI;
                    } else {
                        type = PrivateKeyInfo.TYPE_BRAIN_WALLET;
                    }
                    final boolean isPublicKeyCompressed = false;
                    return new PrivateKeyInfo(type, encodedPrivateKey, privateKeyBigInteger, isPublicKeyCompressed);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static boolean verifyBitcoinAddress(String address) {
        byte[] decodedAddress = decodeBase58(address);
        return !(decodedAddress == null || decodedAddress.length < 6 || decodedAddress[0] != 0 || !verifyChecksum(decodedAddress));
    }

    public static boolean verifyChecksum(byte[] bytesWithChecksumm) {
        try {
            if (bytesWithChecksumm == null || bytesWithChecksumm.length < 5) {
                return false;
            }
            MessageDigest digestSha = MessageDigest.getInstance("SHA-256");
            digestSha.update(bytesWithChecksumm, 0, bytesWithChecksumm.length - 4);
            byte[] first = digestSha.digest();
            byte[] calculatedDigest = digestSha.digest(first);
            boolean checksumValid = true;
            for (int i = 0; i < 4; i++) {
                if (calculatedDigest[i] != bytesWithChecksumm[bytesWithChecksumm.length - 4 + i]) {
                    checksumValid = false;
                }
            }
            return checksumValid;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] sha256ripemd160(byte[] publicKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            //https://en.bitcoin.it/wiki/Technical_background_of_Bitcoin_addresses
            //1 - Take the corresponding public key generated with it (65 bytes, 1 byte 0x04, 32 bytes corresponding to X coordinate, 32 bytes corresponding to Y coordinate)
            //2 - Perform SHA-256 hashing on the public key
            byte[] sha256hash = sha256.digest(publicKey);
            //3 - Perform RIPEMD-160 hashing on the result of SHA-256
            RIPEMD160Digest ripemd160Digest = new RIPEMD160Digest();
            ripemd160Digest.update(sha256hash, 0, sha256hash.length);
            byte[] hashedPublicKey = new byte[20];
            ripemd160Digest.doFinal(hashedPublicKey, 0);
            return hashedPublicKey;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String publicKeyToAddress(byte[] publicKey) {
        try {
            byte[] hashedPublicKey = sha256ripemd160(publicKey);
            //4 - Add version byte in front of RIPEMD-160 hash (0x00 for Main Network)
            byte[] addressBytes = new byte[1 + hashedPublicKey.length + 4];
            addressBytes[0] = 0;
            System.arraycopy(hashedPublicKey, 0, addressBytes, 1, hashedPublicKey.length);
            //5 - Perform SHA-256 hash on the extended RIPEMD-160 result
            //6 - Perform SHA-256 hash on the result of the previous SHA-256 hash
            MessageDigest digestSha = MessageDigest.getInstance("SHA-256");
            digestSha.update(addressBytes, 0, addressBytes.length - 4);
            byte[] check = digestSha.digest(digestSha.digest());
            //7 - Take the first 4 bytes of the second SHA-256 hash. This is the address checksum
            //8 - Add the 4 checksum bytes from point 7 at the end of extended RIPEMD-160 hash from point 4. This is the 25-byte binary Bitcoin Address.
            System.arraycopy(check, 0, addressBytes, hashedPublicKey.length + 1, 4);
            return encodeBase58(addressBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static final int BASE58_CHUNK_DIGITS = 10;//how many base 58 digits fits in long
    private static final BigInteger BASE58_CHUNK_MOD = BigInteger.valueOf(0x5fa8624c7fba400L); //58^BASE58_CHUNK_DIGITS
    private static final byte[] BASE58_VALUES = new byte[]{-1, -1, -1, -1, -1, -1, -1, -1, -1, -2, -2, -2, -2, -2, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, -1, -1, -1, -1, -1, -1,
            -1, 9, 10, 11, 12, 13, 14, 15, 16, -1, 17, 18, 19, 20, 21, -1,
            22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, -1, -1, -1, -1, -1,
            -1, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, -1, 44, 45, 46,
            47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};

    public static byte[] decodeBase58(String input) {
        if (input == null) {
            return null;
        }
        input = input.trim();
        if (input.length() == 0) {
            return new byte[0];
        }
        BigInteger resultNum = BigInteger.ZERO;
        int nLeadingZeros = 0;
        while (nLeadingZeros < input.length() && input.charAt(nLeadingZeros) == BASE58[0]) {
            nLeadingZeros++;
        }
        long acc = 0;
        int nDigits = 0;
        int p = nLeadingZeros;
        while (p < input.length()) {
            int v = BASE58_VALUES[input.charAt(p) & 0xff];
            if (v >= 0) {
                acc *= 58;
                acc += v;
                nDigits++;
                if (nDigits == BASE58_CHUNK_DIGITS) {
                    resultNum = resultNum.multiply(BASE58_CHUNK_MOD).add(BigInteger.valueOf(acc));
                    acc = 0;
                    nDigits = 0;
                }
                p++;
            } else {
                break;
            }
        }
        if (nDigits > 0) {
            long mul = 58;
            while (--nDigits > 0) {
                mul *= 58;
            }
            resultNum = resultNum.multiply(BigInteger.valueOf(mul)).add(BigInteger.valueOf(acc));
        }
        final int BASE58_SPACE = -2;
        while (p < input.length() && BASE58_VALUES[input.charAt(p) & 0xff] == BASE58_SPACE) {
            p++;
        }
        if (p < input.length()) {
            return null;
        }
        byte[] plainNumber = resultNum.toByteArray();
        int plainNumbersOffs = plainNumber[0] == 0 ? 1 : 0;
        byte[] result = new byte[nLeadingZeros + plainNumber.length - plainNumbersOffs];
        System.arraycopy(plainNumber, plainNumbersOffs, result, nLeadingZeros, plainNumber.length - plainNumbersOffs);
        return result;
    }

    public static String encodeBase58(byte[] input) {
        if (input == null) {
            return null;
        }
        StringBuilder str = new StringBuilder((input.length * 350) / 256 + 1);
        BigInteger bn = new BigInteger(1, input);
        long rem;
        while (true) {
            BigInteger[] divideAndRemainder = bn.divideAndRemainder(BASE58_CHUNK_MOD);
            bn = divideAndRemainder[0];
            rem = divideAndRemainder[1].longValue();
            if (bn.compareTo(BigInteger.ZERO) == 0) {
                break;
            }
            for (int i = 0; i < BASE58_CHUNK_DIGITS; i++) {
                str.append(BASE58[(int) (rem % 58)]);
                rem /= 58;
            }
        }
        while (rem != 0) {
            str.append(BASE58[(int) (rem % 58)]);
            rem /= 58;
        }
        str.reverse();
        int nLeadingZeros = 0;
        while (nLeadingZeros < input.length && input[nLeadingZeros] == 0) {
            str.insert(0, BASE58[0]);
            nLeadingZeros++;
        }
        return str.toString();
    }





    public static String encodeWifKey(boolean isPublicKeyCompressed, byte[] secret) {
        try {
            MessageDigest digestSha = MessageDigest.getInstance("SHA-256");
            byte[] rawPrivateKey = new byte[isPublicKeyCompressed ? 38 : 37];
            rawPrivateKey[0] = (byte) 0x80;
            if (isPublicKeyCompressed) {
                rawPrivateKey[rawPrivateKey.length - 5] = 1;
            }
            System.arraycopy(secret, 0, rawPrivateKey, 1, secret.length);
            digestSha.update(rawPrivateKey, 0, rawPrivateKey.length - 4);
            byte[] check = digestSha.digest(digestSha.digest());
            System.arraycopy(check, 0, rawPrivateKey, rawPrivateKey.length - 4, 4);
            return encodeBase58(rawPrivateKey);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        final char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] fromHex(String s) {
        if (s != null) {
            try {
                StringBuilder sb = new StringBuilder(s.length());
                for (int i = 0; i < s.length(); i++) {
                    char ch = s.charAt(i);
                    if (!Character.isWhitespace(ch)) {
                        sb.append(ch);
                    }
                }
                s = sb.toString();
                int len = s.length();
                byte[] data = new byte[len / 2];
                for (int i = 0; i < len; i += 2) {
                    int hi = (Character.digit(s.charAt(i), 16) << 4);
                    int low = Character.digit(s.charAt(i + 1), 16);
                    if (hi >= 256 || low < 0 || low >= 16) {
                        return null;
                    }
                    data[i / 2] = (byte) (hi | low);
                }
                return data;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static byte[] sign(BigInteger privateKey, byte[] input) {
        synchronized (EC_PARAMS) {
            ECDSASigner signer = new ECDSASigner();
            ECPrivateKeyParameters privateKeyParam = new ECPrivateKeyParameters(privateKey, EC_PARAMS);
            signer.init(true, new ParametersWithRandom(privateKeyParam, SECURE_RANDOM));
            BigInteger[] sign = signer.generateSignature(input);
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(72);
                DERSequenceGenerator derGen = new DERSequenceGenerator(baos);
                derGen.addObject(new ASN1Integer(sign[0]));
                derGen.addObject(new ASN1Integer(sign[1]));
                derGen.close();
                return baos.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static boolean verify(byte[] publicKey, byte[] signature, byte[] msg) {
        synchronized (EC_PARAMS) {
            boolean valid;
            ECDSASigner signerVer = new ECDSASigner();
            try {
                ECPublicKeyParameters pubKey = new ECPublicKeyParameters(EC_PARAMS.getCurve().decodePoint(publicKey), EC_PARAMS);
                signerVer.init(false, pubKey);
                ASN1InputStream derSigStream = new ASN1InputStream(signature);
                DLSequence seq = (DLSequence) derSigStream.readObject();
                BigInteger r = ((ASN1Integer) seq.getObjectAt(0)).getPositiveValue();
                BigInteger s = ((ASN1Integer) seq.getObjectAt(1)).getPositiveValue();
                derSigStream.close();
                valid = signerVer.verifySignature(msg, r, s);
            } catch (IOException e) {
                throw new RuntimeException();
            }
            return valid;
        }
    }

    public static byte[] reverse(byte[] bytes) {
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = bytes[bytes.length - i - 1];
        }
        return result;
    }

    public static byte[] reverseInPlace(byte[] bytes) {
        int len = bytes.length / 2;
        for (int i = 0; i < len; i++) {
            byte t = bytes[i];
            bytes[i] = bytes[bytes.length - i - 1];
            bytes[bytes.length - i - 1] = t;
        }
        return bytes;
    }
}