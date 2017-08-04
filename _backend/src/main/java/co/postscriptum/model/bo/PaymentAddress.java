package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentAddress {

    String uuid;

    String btcAddress;

    long assignedTime;

}
