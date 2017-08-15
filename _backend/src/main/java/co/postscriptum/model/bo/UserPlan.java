package co.postscriptum.model.bo;

import co.postscriptum.internal.Utils;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserPlan {

    private List<Payment> payments;

    private long paidUntil;

    public void addPayment(Payment payment) {
        if (payments == null) {
            payments = new ArrayList<>();
        }
        payments.add(payment);
        paidUntil = System.currentTimeMillis() + Utils.daysToMillis(365);
    }

    public boolean needPayment() {
        return paidUntil < System.currentTimeMillis();
    }

    @Value
    @Builder
    public static class Payment {

        private long time;

        private String details;

        private String amount;

    }

}
