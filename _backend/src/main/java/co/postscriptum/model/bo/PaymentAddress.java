package co.postscriptum.model.bo;

import lombok.Data;

@Data
public class PaymentAddress {
    String uuid;
    String btcAddress;

    long assignedTime;
}
