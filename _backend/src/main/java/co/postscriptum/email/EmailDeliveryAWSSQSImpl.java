package co.postscriptum.email;

import co.postscriptum.internal.AwsConfig;
import co.postscriptum.internal.Utils;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Profile(value = {"prod", "cert"})
@Component
public class EmailDeliveryAWSSQSImpl implements EmailDelivery {

    @Autowired
    private AwsConfig awsConfig;

    private AmazonSQSAsync sqsClient;

    private String queueUrl;

    @PostConstruct
    public void init() {
        log.info("connecting to AWS SQS ...");

        sqsClient = AmazonSQSAsyncClientBuilder.standard()
                                               .withCredentials(awsConfig.awsCredentialsProvider())
                                               .withRegion(awsConfig.getSqsRegion())
                                               .build();

        queueUrl = awsConfig.getSqsQueueName();

        log.info("using queue={}", queueUrl);

        log.info("connected to AWS SQS");

    }

    private Map<String, String> fromHeaderData(String data) {
        try {
            String json = new String(Utils.base64decode(data), StandardCharsets.UTF_8);
            try (Reader fr = new StringReader(json)) {
                return Utils.fromJson(fr, new TypeToken<Map<String, String>>() {
                });
            }
        } catch (Exception e) {
            log.error("can't convert ENVELOPE_HEADER_DATA to Map");
            return new HashMap<>();
        }
    }

    @Override
    public void process(OnDelivery onDelivery) {

        ReceiveMessageRequest request = new ReceiveMessageRequest();
        request.setMaxNumberOfMessages(10);
        request.setQueueUrl(queueUrl);
        request.setWaitTimeSeconds(5);

        ReceiveMessageResult result;
        do {

            log.info("call AWSSQS::receiveMessage");

            result = sqsClient.receiveMessage(request);

            for (Message message : result.getMessages()) {
                Map<String, Object> body = Utils.mapFromJson(message.getBody());

                Map<String, Object> Message = Utils.mapFromJson((String) body.get("Message"));

                String messageId = getMessageId(Message);
                String envelopeId = getHeader(Message, "ENVELOPE_ID");
                String headerData = getHeader(Message, "ENVELOPE_HEADER_DATA");

                DeliveryType deliveryType = getDeliveryType(Message);

                Map<String, Object> bounceCause = null;
                if (deliveryType == DeliveryType.Bounce) {
                    bounceCause = (Map<String, Object>) Message.get("bounce");
                }

                onDelivery.onDelivery(messageId, envelopeId, fromHeaderData(headerData), getDeliveryType(Message), bounceCause);

                log.info("call AWSSQS::deleteMessage");
                sqsClient.deleteMessage(queueUrl, message.getReceiptHandle());

            }

        } while (result.getMessages().size() > 0);

    }

    private DeliveryType getDeliveryType(Map<String, Object> Message) {
        String notificationType = (String) Message.get("notificationType");
        return DeliveryType.valueOf(notificationType);
    }

    private String getMessageId(Map<String, Object> Message) {
        Map<String, Object> mail = (Map<String, Object>) Message.get("mail");
        return (String) mail.get("messageId");
    }

    private String getHeader(Map<String, Object> Message, String headerName) {
        Map<String, Object> mail = (Map<String, Object>) Message.get("mail");
        List<Map<String, Object>> mailHeaders = (List<Map<String, Object>>) mail.get("headers");
        for (Map<String, Object> o : mailHeaders) {
            if (o.get("name").equals(headerName)) {
                return (String) o.get("value");
            }
        }
        return null;
    }

}
