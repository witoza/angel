package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class UserPlan {

    List<Payment> payments;
    long paidUntil;

    @Data
    @Builder
    public static class Payment {
        private final long time;
        private final String details;
        private final String amount;
    }

}
