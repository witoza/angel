package co.postscriptum.test_data;

import co.postscriptum.model.bo.UserData;

import java.io.IOException;
import java.security.PublicKey;

public interface TestUser {

    String getUsername();

    UserData createUserData(PublicKey adminPublicKey) throws IOException;

}
