package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentAddress {

    private String uuid;

    private String btcAddress;

    private long assignedTime;

}
