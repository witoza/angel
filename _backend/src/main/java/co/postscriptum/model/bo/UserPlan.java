package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Data;
import lombok.Value;

import java.util.List;

@Data
public class UserPlan {

    private List<Payment> payments;
    private long paidUntil;

    @Value
    @Builder
    public static class Payment {

        private long time;

        private String details;

        private String amount;

    }

}
