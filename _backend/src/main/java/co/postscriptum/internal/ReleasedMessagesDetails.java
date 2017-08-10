package co.postscriptum.internal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ReleasedMessagesDetails {

    private final long releasedTime = System.currentTimeMillis();
    private final List<ReleasedMessage> details = new ArrayList<>();

    public void add(ReleasedMessage releasedMessage) {
        details.add(releasedMessage);
    }

    @Value
    @AllArgsConstructor
    public static class ReleasedMessage {

        private final String messageTitle;

        private final List<String> invalidRecipients = new ArrayList<>();

        private final List<String> sentToRecipients = new ArrayList<>();

    }

}
