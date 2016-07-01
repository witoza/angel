package co.postscriptum.email;

public interface EmailDeliveryDummy extends EmailDelivery {

    void markAsDelivered(Envelope envelope, String messageId);
}
