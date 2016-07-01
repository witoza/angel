package co.postscriptum.email;

import co.postscriptum.RuntimeEnvironment;
import co.postscriptum.internal.AwsConfig;
import co.postscriptum.internal.Utils;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.RawMessage;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Profile(value = {"prod", "cert"})
@Component
public class EmailSenderAWSSESImpl implements EmailSender {

    @Autowired
    private RuntimeEnvironment env;

    @Autowired
    private AwsConfig awsConfig;

    @Autowired
    private EmailTemplateService emailTemplateService;

    private AmazonSimpleEmailService service;

    @PostConstruct
    public void init() {

        log.info("using from domain={}", env.getDomain());
        log.info("connecting to AWS SES ...");

        service = AmazonSimpleEmailServiceClientBuilder.standard()
                                                       .withCredentials(
                                                               new AWSStaticCredentialsProvider(
                                                                       new BasicAWSCredentials(awsConfig.getAccessKeyId(),
                                                                                               awsConfig.getSecretAccessKey())))
                                                       .withRegion(awsConfig.getSesRegion())
                                                       .build();

        log.info("connected to AWS SES");

    }

    private String toHeaderData(Map<String, String> headers) {
        return Utils.base64encode(Utils.toJson(headers).getBytes(StandardCharsets.UTF_8));
    }

    private RawMessage buildSimpleRawMessage(Envelope envelope) {
        try {
            // JavaMail representation of the message
            Session session = Session.getDefaultInstance(new Properties());
            MimeMessage mimeMessage = new MimeMessage(session);

            mimeMessage.setFrom(new InternetAddress("contact@" + env.getDomain(), env.getDomain()));
            mimeMessage.setSubject(envelope.getTitle(), "UTF-8");
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(envelope.getRecipient()));

            // Add a MIME part to the message
            MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setContent(emailTemplateService.getFormattedContent(envelope), "text/html; charset=utf-8");

            MimeMultipart mimeBodyPart = new MimeMultipart();
            mimeBodyPart.addBodyPart(bodyPart);

            mimeMessage.setContent(mimeBodyPart);
            mimeMessage.addHeader("ENVELOPE_ID", envelope.getEnvelopeId());
            mimeMessage.addHeader("ENVELOPE_HEADER_DATA", toHeaderData(envelope.getHeaders()));

            // Create Raw message
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            mimeMessage.writeTo(outputStream);
            return new RawMessage(ByteBuffer.wrap(outputStream.toByteArray()));
        } catch (Exception e) {
            throw new IllegalStateException("can't prepare email raw message", e);
        }
    }

    public String sendEmail(Envelope envelope) {

        if (!Utils.isValidEmail(envelope.getRecipient())) {
            throw new IllegalArgumentException("recipient email address '" + envelope.getRecipient() + "' is invalid");
        }

        log.info("sending email: envelopeId={}, title={}, recipient={}",
                 envelope.getEnvelopeId(),
                 envelope.getTitle(),
                 envelope.getRecipient());

        RawMessage rawMessage = buildSimpleRawMessage(envelope);

        SendRawEmailResult result = service.sendRawEmail(new SendRawEmailRequest(rawMessage));

        String messageId = result.getMessageId();
        log.info("email has been sent: messageId={}", messageId);

        return messageId;
    }

}
