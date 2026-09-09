/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package atm.gob.ec.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.net.URI;

import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

/**
 *
 * @author erik.flores
 */

public class Utils {
    
    private static final Logger logger = LogManager.getLogger(Utils.class);
    private static final String PROPERTIES_PATH = "resources/resourcesATM.properties";
    private static final String LOG4J2_CONFIG_PATH = "resources/log4j2.xml";

    private Utils() {
        // Constructor privado para evitar instanciación
    }

    public static String getDirectorioSistema() {
        String userDir = System.getProperty("user.dir").replace("\\", "/");
        return userDir.startsWith("file:") ? userDir.substring(5) : userDir;
    }

    public static Properties getProperties() {
        Properties properties = new Properties();
        String filePath = getDirectorioSistema() + "/" + PROPERTIES_PATH;

        if (!new File(filePath).exists()) {
            logger.error("El archivo de propiedades no se encontró en: {}", filePath);
            return properties;
        }

        try (FileInputStream fileInputStream = new FileInputStream(filePath)) {
            properties.load(fileInputStream);
        } catch (IOException e) {
            logger.error("Error al cargar el archivo de propiedades", e);
        }
        return properties;
    }

    public static LoggerContext configureLogging() {
        return configureLogging(getDirectorioSistema() + "/" + LOG4J2_CONFIG_PATH);
    }

    public static LoggerContext configureLogging(String dirLog4j2) {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        File logConfigFile = new File(dirLog4j2);

        if (!logConfigFile.exists()) {
            throw new RuntimeException("Archivo de configuración log4j2 no encontrado en: " + dirLog4j2);
        }

        try {
            URI uri = logConfigFile.toURI();
            context.setConfigLocation(uri);
            logger.info("Configuración de log4j2 cargada desde: {}", dirLog4j2);
        } catch (Exception e) {
            throw new RuntimeException("Error al configurar log4j2", e);
        }
        return context;
    }
}


