package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class User {

    private String uuid;

    private boolean active;

    private boolean tosAccepted;

    private long lastAccess;

    private String username;

    private Role role;

    private Trigger trigger;

    private PaymentAddress paymentAddress;

    public enum Role {
        user,
        admin
    }

}
