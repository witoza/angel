package co.postscriptum.email;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
public class Envelope {

    private final EnvelopeType type;
    private final String envelopeId;
    private final String recipient;
    private final String title;
    private final String msgHeader;
    private final String msgContent;
    private final String msgFooter;
    private final Map<String, String> headers;

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
            this.headers = headers;
        }
        this.envelopeId = type.name() + "#" + UUID.randomUUID().toString();
        this.recipient = recipient;
        this.title = title;
        this.msgHeader = msgHeader;
        this.msgContent = msgContent;
        this.msgFooter = msgFooter;

    }

}
