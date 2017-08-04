package co.postscriptum.email;

import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Value
public class Envelope {

    private EnvelopeType type;

    private String envelopeId;

    private String recipient;

    private String title;

    private String msgHeader;

    private String msgContent;

    private String msgFooter;

    private Map<String, String> headers;

    @Builder
    private Envelope(EnvelopeType type,
                     Map<String, String> headers,
                     String recipient,
                     String title,
                     String msgHeader,
                     String msgContent,
                     String msgFooter) {

        this.type = type;
        if (headers == null) {
            this.headers = new HashMap<>();
        } else {
            this.headers = ImmutableMap.copyOf(headers);
        }
        this.envelopeId = type.name() + "#" + UUID.randomUUID().toString();
        this.recipient = recipient;
        this.title = title;
        this.msgHeader = msgHeader;
        this.msgContent = msgContent;
        this.msgFooter = msgFooter;
    }

}
