package co.postscriptum.security;

import co.postscriptum.model.bo.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collections;

public class MyAuthenticationToken extends UsernamePasswordAuthenticationToken {

    private final User.Role role;

    public MyAuthenticationToken(String username, User.Role role, GrantedAuthority authority) {
        super(username, null, Collections.singletonList(authority));
        this.role = role;
    }

    public User.Role getRole() {
        return role;
    }

}