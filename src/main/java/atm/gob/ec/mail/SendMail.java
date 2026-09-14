/**
 *
 * @author erik.flores
 */

package atm.gob.ec.mail;

import atm.gob.ec.security.AesCryptoService;
import atm.gob.ec.security.CryptoService;
import atm.gob.ec.utils.Utils;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import java.io.File;

import java.util.Date;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SendMail {
    
    private static final Logger logger = LogManager.getLogger(SendMail.class);
    private static final Properties properties = Utils.getProperties();
    private static String secret = System.getProperty("atm.crypto.key");

    private SendMail() {
        // Constructor privado para evitar instanciación
    }

    /**
     * Método send() sobrecargado que obtiene los datos de envío de correo desde el archivo de propiedades.
     * @param ps_to
     * @param ps_cc
     * @param ps_bcc
     * @param ps_subject
     * @param ps_message
     * @param ps_attachments
     * @return 
     */
    public static String send(String ps_to, String ps_cc, String ps_bcc, String ps_subject, 
                              String ps_message, String ps_attachments) {

        String ps_smtp = properties.getProperty("MAIL.SERVER");
        String ps_port = properties.getProperty("MAIL.PORT");
        String ps_from;
        String ps_password;

        try {
            CryptoService crypto = new AesCryptoService(secret);
            ps_from = crypto.decrypt(properties.getProperty("MAIL.FROM"));
            ps_password = crypto.decrypt(properties.getProperty("MAIL.PASS"));
        } catch (Exception e) {
            logger.error("Error desencriptando credenciales de correo", e);
            return "Error obteniendo credenciales de correo.";
        }

        return send(ps_smtp, ps_from, ps_to, ps_cc, ps_bcc, ps_subject, ps_message, ps_password, ps_port, ps_attachments);
    }

    /**
     * Método principal de envío de correo.
     * @param ps_smtp
     * @param ps_from
     * @param ps_to
     * @param ps_cc
     * @param ps_bcc
     * @param ps_subject
     * @param ps_message
     * @param ps_password
     * @param ps_port
     * @param ps_attachments
     * @return 
     */
    public static String send(String ps_smtp, String ps_from, String ps_to, String ps_cc, 
                              String ps_bcc, String ps_subject, String ps_message, 
                              String ps_password, String ps_port, String ps_attachments) {

        if (ps_to == null || ps_to.isEmpty()) {
            return "Por favor, indique la dirección de correo.";
        }

        return (ps_attachments == null || ps_attachments.isEmpty()) 
            ? sendEmail(ps_smtp, ps_from, ps_to, ps_cc, ps_bcc, ps_subject, ps_message, ps_password, ps_port)
            : sendEmailWithAttachments(ps_smtp, ps_from, ps_to, ps_cc, ps_bcc, ps_subject, ps_message, ps_password, ps_port, ps_attachments);
    }

    private static String sendEmail(String ps_smtp, String ps_from, String ps_to, String ps_cc, 
                                    String ps_bcc, String ps_subject, String ps_message, 
                                    String ps_password, String ps_port) {
        try {
            Session session = createMailSession(ps_smtp, ps_from, ps_password, ps_port);
            Message message = createMessage(session, ps_from, ps_to, ps_cc, ps_bcc, ps_subject, ps_message);
            Transport.send(message);
            logger.info("Correo enviado exitosamente.");
            return "";
        } catch (MessagingException e) {
            logger.error("Error al enviar correo", e);
            return e.toString();
        }
    }

    private static String sendEmailWithAttachments(String ps_smtp, String ps_from, String ps_to, String ps_cc, 
                                                   String ps_bcc, String ps_subject, String ps_message, 
                                                   String ps_password, String ps_port, String ps_attachments) {
        try {
            Session session = createMailSession(ps_smtp, ps_from, ps_password, ps_port);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(ps_from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(ps_to.replace(";", ",")));
            if (ps_cc != null) message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(ps_cc.replace(";", ",")));
            if (ps_bcc != null) message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(ps_bcc.replace(";", ",")));
            message.setSubject(ps_subject);

            // Adjuntar archivos
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(ps_message, "text/html");

            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.setDataHandler(new DataHandler(new FileDataSource(ps_attachments)));
            attachmentPart.setFileName(new File(ps_attachments).getName());

            MimeMultipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(attachmentPart);

            message.setContent(multipart);
            message.setSentDate(new Date());

            Transport.send(message);
            logger.info("Correo con adjunto enviado exitosamente.");
            return "";
        } catch (MessagingException e) {
            logger.error("Error al enviar correo con adjunto", e);
            return e.toString();
        }
    }

    private static Session createMailSession(String ps_smtp, String ps_from, String ps_password, String ps_port) {
        Properties props = new Properties();
        props.put("mail.smtp.host", ps_smtp);
        props.put("mail.smtp.port", ps_port);
        props.put("mail.smtp.auth", "true");

        if ("587".equals(ps_port)) {
            props.put("mail.smtp.starttls.enable", "true");
        } else if ("465".equals(ps_port)) {
            props.put("mail.smtp.ssl.enable", "true");
        }

        Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(ps_from, ps_password);
            }
        };

        return Session.getInstance(props, authenticator);
    }

    private static Message createMessage(Session session, String ps_from, String ps_to, String ps_cc, 
                                         String ps_bcc, String ps_subject, String ps_message) throws MessagingException {
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(ps_from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(ps_to.replace(";", ",")));
        if (ps_cc != null) message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(ps_cc.replace(";", ",")));
        if (ps_bcc != null) message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(ps_bcc.replace(";", ",")));
        message.setSubject(ps_subject);
        message.setContent(ps_message, "text/html");
        message.setSentDate(new Date());
        return message;
    }
}
