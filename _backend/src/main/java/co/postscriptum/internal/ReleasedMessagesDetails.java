package co.postscriptum.internal;

import co.postscriptum.model.bo.Message;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReleasedMessagesDetails {

    private final List<Pair<String, Map<String, String>>> details = new ArrayList<>();

    public void add(Message message, Map<String, String> recipientDetails) {
        details.add(Pair.of(message.getTitle(), recipientDetails));
    }

    public List<Pair<String, Map<String, String>>> getDetails() {
        return details;
    }
}
