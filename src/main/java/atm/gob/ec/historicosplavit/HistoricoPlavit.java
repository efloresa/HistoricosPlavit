/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package atm.gob.ec.historicosplavit;

import atm.gob.ec.encriptacion.KeyManager;
import atm.gob.ec.mail.SendMail;
import atm.gob.ec.utils.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.Locale;

import java.util.Properties;
import java.util.regex.Pattern;
import javax.crypto.SecretKey;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

/**
 *
 * @author erik.flores
 */
public class HistoricoPlavit {
    
    private static final Logger logger = LogManager.getLogger(HistoricoPlavit.class);
    private static final LoggerContext context = Utils.configureLogging();
    private static final Properties propertie = Utils.getProperties();
    private static final String SEPARADOR = Pattern.quote("|");
    private static final SimpleDateFormat FORMATO = new SimpleDateFormat("dd-MMMMM-yyyy", new Locale("es", "ES"));

    public HistoricoPlavit() throws Exception {
        
        logger.info("Inicialización de HistoricoPlavit completada.");
    }

    private static Connection conectar() throws Exception {
        SecretKey key = KeyManager.loadKey();
        String url = propertie.getProperty("DB.URL");
        String username = KeyManager.decrypt(propertie.getProperty("DB.USER"), key);
        String password = KeyManager.decrypt(propertie.getProperty("DB.PASSWD"), key);
        
        Class.forName(propertie.getProperty("DB.DRIVER"));
        logger.info("Intentando conexión a la base de datos...");
        
        return DriverManager.getConnection(url, username, password);
    }

    public void moverHistorica(String tabla) {
        String intervalo = propertie.getProperty("SQL.INTERVALO");
        String mensaje = "";

        // Obtener consultas Q1, Q2 y Q3 desde el archivo de propiedades
        String strSentencia1 = propertie.getProperty(tabla + ".Q1");
        String strSentencia2 = propertie.getProperty(tabla + ".Q2");
        String strSentencia3 = propertie.getProperty(tabla + ".Q3");

        Connection connection = null;
        PreparedStatement ps1 = null;
        PreparedStatement ps2 = null;
        PreparedStatement ps3 = null;
        ResultSet rs = null;

        try {
            connection = conectar();
            connection.setAutoCommit(false); // Iniciar transacción

            logger.info("Iniciando depuración de: " + tabla);

            // Ejecutar Q1 (contar registros a depurar)
            ps1 = connection.prepareStatement(strSentencia1);
            ps1.setString(1, intervalo);
            rs = ps1.executeQuery();
            
            int cantidad = (rs.next()) ? rs.getInt("cantidad") : 0;
            logger.info("Cantidad de registros a depurar: " + cantidad);
            mensaje += "<br>Cantidad de registros a depurar: " + cantidad;

            if (cantidad > 0) {
                // Ejecutar Q2 (mover a tabla histórica)
                ps2 = connection.prepareStatement(strSentencia2);
                ps2.setString(1, intervalo);
                int enviados = ps2.executeUpdate();
                
                logger.info("Registros enviados a histórico: " + enviados);
                mensaje += "<br>Registros enviados a histórico: " + enviados;

                // Ejecutar Q3 (eliminar de la tabla original)
                ps3 = connection.prepareStatement(strSentencia3);
                ps3.setString(1, intervalo);
                int eliminados = ps3.executeUpdate();
                
                logger.info("Registros eliminados: " + eliminados);
                mensaje += "<br>(\"Registros eliminados: " + eliminados;                
                
                connection.commit();
            } else {
                logger.warn("No existen eventos para depurar en " + tabla);
                throw new Exception("No existen eventos para depurar en " + tabla);
            }

            enviaNotificacion(tabla, mensaje);

        } catch (SQLException e) {
            logger.error("Error SQL en mover historico de " + tabla, e);
            mensaje = "Error en mover historico de " + tabla + " <br> " + e.getMessage();
            enviaNotificacion(tabla, mensaje);

            if (connection != null) {
                try {
                    connection.rollback();
                    logger.warn("Rollback ejecutado.");
                } catch (SQLException ex) {
                    logger.error("Error al ejecutar rollback", ex);
                }
            }

        } catch (Exception e) {
            logger.error("Error en mover histórico de " + tabla, e);
            mensaje = "Error en mover histórico de " + tabla + " <br> " + e.getMessage();
            enviaNotificacion(tabla, mensaje);
        } finally {
            // Cerrar recursos en orden inverso a su apertura
            try { if (rs != null) rs.close(); } catch (SQLException e) { logger.warn("Error cerrando ResultSet", e); }
            try { if (ps1 != null) ps1.close(); } catch (SQLException e) { logger.warn("Error cerrando PreparedStatement (Q1)", e); }
            try { if (ps2 != null) ps2.close(); } catch (SQLException e) { logger.warn("Error cerrando PreparedStatement (Q2)", e); }
            try { if (ps3 != null) ps3.close(); } catch (SQLException e) { logger.warn("Error cerrando PreparedStatement (Q3)", e); }
            try { if (connection != null) connection.close(); } catch (SQLException e) { logger.warn("Error cerrando conexión", e); }
        }
    }

    private void enviaNotificacion(String asunto, String mensaje) {
        String asuntoCorreo = propertie.getProperty("MAIL.SUBJECT") + asunto + " del " + FORMATO.format(new Date());
        String mensajeCorreo = propertie.getProperty("MAIL.BODY") + " " + asunto + " de PLAVIT.<br>" + mensaje;

        try {
            if (mensajeCorreo.isEmpty()) {
                throw new Exception("El cuerpo del correo no puede ser nulo");
            }

            String email = SendMail.send(
                propertie.getProperty("MAIL.TO"),
                propertie.getProperty("MAIL.CC"),
                propertie.getProperty("MAIL.BCC"),
                asuntoCorreo,
                mensajeCorreo, 
                ""
            );

            logger.info("Notificación por email enviada correctamente.");
            if (!email.isEmpty()) {
                throw new Exception("Error al enviar notificación por correo: " + email);
            }
        } catch (Exception e) {
            logger.warn("Error enviando la notificación por correo", e);
        }
    }

    public void listaTablas() {
        logger.info("Obteniendo listado de tablas a depurar");

        String tablasConfig = propertie.getProperty("LIST.TABLAS");
        if (tablasConfig == null || tablasConfig.isEmpty()) {
            logger.warn("No hay tablas configuradas para depuración.");
            return;
        }

        String[] tablas = tablasConfig.split(SEPARADOR);
        for (String tabla : tablas) {
            moverHistorica(tabla);
        }
    }

    public static void main(String[] args) {
        logger.info("Inicio del proceso en HistoricosPlavit");

        try {
            HistoricoPlavit depura = new HistoricoPlavit();
            depura.listaTablas();
        } catch (Exception e) {
            logger.error("Error en la ejecución de HistoricosPlavit", e);
        }

        logger.info("Fin del proceso en HistoricosPlavit");
    }
}