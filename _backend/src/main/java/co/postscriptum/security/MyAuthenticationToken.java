package co.postscriptum.security;

import co.postscriptum.model.bo.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collections;
import java.util.Optional;

public class MyAuthenticationToken extends UsernamePasswordAuthenticationToken {

    private final User.Role role;

    private byte[] key;

    public MyAuthenticationToken(String username, User.Role role, byte[] key, GrantedAuthority authority) {
        super(username, null, Collections.singletonList(authority));
        this.role = role;
        this.key = key;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public Optional<byte[]> getKey() {
        return Optional.ofNullable(key);
    }

    public User.Role getRole() {
        return role;
    }

}