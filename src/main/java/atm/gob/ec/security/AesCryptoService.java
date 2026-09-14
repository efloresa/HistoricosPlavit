/**
 *
 * @author erik.flores 
 * ENC2
 ├── PBKDF2WithHmacSHA256
 ├── 120.000 iteraciones
 ├── AES-256
 ├── GCM
 ├── salt = 16 bytes
 ├── nonce = 12 bytes
 └── authentication tag = 128 bits
 */

package atm.gob.ec.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import java.security.SecureRandom;

import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class AesCryptoService implements CryptoService {

    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String PBKDF2 = "PBKDF2WithHmacSHA256";

//    private static final String VERSION_V1 = "ENC1:";
    private static final String VERSION_V2 = "ENC2:";

    private static final int AES_KEY_SIZE = 256; 

    private static final int SALT_LENGTH = 16; 
    private static final int IV_LENGTH = 12;

    private static final int GCM_TAG_LENGTH = 128; 

    private static final int PBKDF2_ITERATIONS = 120_000;

    private final String secret;

    private final SecureRandom secureRandom;

    public AesCryptoService(String secret) {

        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("Secret key cannot be null or empty");
        }

        this.secret = secret;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public String encrypt(String value) {

        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Value to encrypt cannot be null or empty");
        }

        try {

            byte [] salt = generateRandomBytes(SALT_LENGTH);
            byte [] iv = generateRandomBytes(IV_LENGTH);

            SecretKeySpec key = buildKeyV2(salt);

            Cipher cipher = Cipher.getInstance(AES_GCM);

            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)); 

            byte[] payload = buildPayload(salt, iv, encrypted);

            return VERSION_V2 + Base64.getEncoder().encodeToString(payload);

        } catch (Exception ex) {
            throw new RuntimeException("Error encrypting value", ex);
        }
    }

    @Override
    public String decrypt(String value) {

        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Value to decrypt cannot be null or empty");
        }

        try {

            if(value.startsWith(VERSION_V2)){
                return decryptV2(value);
            }else{
                /*
                 * Compatibilidad con los valores cifrados
                 * mediante la implementación anterior.
                 */

                return decryptV1(value);
            }

        } catch (Exception ex) {
            throw new RuntimeException("Error decrypting value", ex);
        }
    }

    /**
     * AES-GCM / PBKDF2
     */
    private String decryptV2(String value) throws Exception {

        String encodePayload = value.substring(VERSION_V2.length());

        byte[] payload = Base64.getDecoder().decode(encodePayload);

        ByteBuffer buffer = ByteBuffer.wrap(payload);

        byte [] salt = new byte[SALT_LENGTH];
        byte [] iv = new byte[IV_LENGTH];

        buffer.get(salt);
        buffer.get(iv);

        byte [] encrypted = new byte[buffer.remaining()];

        buffer.get(encrypted);
        
        SecretKeySpec key = buildKeyV2(salt);

        Cipher cipher = Cipher.getInstance(AES_GCM);

        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        byte [] decrypted = cipher.doFinal(encrypted);

        return new String(decrypted, StandardCharsets.UTF_8);

    }

    /**
     * Implementación anterior.
     *
     * Se mantiene únicamente para poder descifrar
     * credenciales existentes.
     */
    private String decryptV1(String value) throws Exception {

            SecretKeySpec key = buildKeyV1();

            Cipher cipher = Cipher.getInstance(AES);

            cipher.init(Cipher.DECRYPT_MODE, key);

            byte[] decoded = Base64.getDecoder().decode(value);

            byte[] decrypted = cipher.doFinal(decoded);

            return new String(decrypted, StandardCharsets.UTF_8);

    }

    /**
     * Deriva la clave AES-256 a partir del secreto
     * proporcionado por el entorno.
     */
    private SecretKeySpec buildKeyV2(byte [] salt) throws Exception{

        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2);
        byte [] key = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, AES);
    }

    /**
     * Mantiene exactamente el mecanismo utilizado
     * por la implementación anterior.
     */
    private SecretKeySpec buildKeyV1() throws Exception {

        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(key, AES);
    }

    private byte [] generateRandomBytes (int length){

        byte [] bytes = new byte[length];
        secureRandom.nextBytes(bytes);

        return bytes;
    }

    /**
     * Payload:
     *
     * SALT | IV | CIPHERTEXT + TAG
     */
    private byte[] buildPayload(byte[] salt, byte[] iv, byte[] encrypted) {

        ByteBuffer buffer = ByteBuffer.allocate(salt.length + iv.length + encrypted.length);
        buffer.put(salt);
        buffer.put(iv);
        buffer.put(encrypted);

        return buffer.array();
    }

}

