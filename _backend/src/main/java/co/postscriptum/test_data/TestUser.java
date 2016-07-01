package co.postscriptum.test_data;

import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;

import java.io.IOException;
import java.security.PublicKey;

public interface TestUser {

    User createUser();

    UserData createUserData(User user, PublicKey adminPublicKey) throws IOException;

}
