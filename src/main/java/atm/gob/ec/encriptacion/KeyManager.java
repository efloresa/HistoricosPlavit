/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package atm.gob.ec.encriptacion;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.Base64;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author erik.flores
 */
public class KeyManager {

//    private static final Logger LOGGER = LogManager.getLogger(KeyManager.class);
    
    private static final String ALGORITHM = "AES"; 
    private static final String KEY_FILE = "encryption.key";
    private static final String PROPS_FILE = "config.properties";
    private static final String SYSTEM_DIRECTORY = getDirectorioSistema() + "/resources/";
    
    public static void generateAndStoreKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(128); // AES-128, puedes usar 192 o 256 bits también
        SecretKey secretKey = keyGen.generateKey();
        byte[] encodedKey = Base64.getEncoder().encode(secretKey.getEncoded());

        try (FileOutputStream keyOut = new FileOutputStream(SYSTEM_DIRECTORY + KEY_FILE)) {
            keyOut.write(encodedKey);
        } catch (IOException e) {
            throw new Exception("Error al guardar la clave de encriptación", e);
        }
    }

    public static SecretKey loadKey() throws Exception {
        try {
            byte[] encodedKey = Files.readAllBytes(Paths.get(SYSTEM_DIRECTORY + KEY_FILE));
            byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
            return new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);
        } catch (IOException e) {
            throw new Exception("Error al cargar la clave de encriptación", e);
        }
    }
    
    public static String encrypt(String data, SecretKey key) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encryptedData = cipher.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(encryptedData);
        } catch (Exception e) {
            throw new Exception("Error al encriptar los datos", e);
        }
    }
    
    public static String decrypt(String encryptedData, SecretKey key) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] originalData = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            return new String(originalData);
        } catch (Exception e) {
            throw new Exception("Error al desencriptar los datos", e);
        }
    }
    
    public static String getDirectorioSistema(){ 
        String strDirSistema = System.getProperty("user.dir").replaceAll("\\\\", "/");
        return strDirSistema.contains("file:")? strDirSistema.substring(5) : strDirSistema;        
    }
    
    public static Properties getProperties(){
        Properties properties = new Properties();
        FileInputStream file = null;
        try {
            file = new FileInputStream(SYSTEM_DIRECTORY + PROPS_FILE);
            properties.load(file);
            file.close();
        } catch (FileNotFoundException ex) {
//            LOGGER.warn(ex);
            System.exit(-1);
        } catch (IOException ex) {
//            LOGGER.warn(ex);
            System.exit(-1);
        }        
        return properties;
    }
    
    
}

