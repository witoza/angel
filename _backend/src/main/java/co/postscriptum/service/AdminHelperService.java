package co.postscriptum.service;

import co.postscriptum.db.DB;
import co.postscriptum.email.Envelope;
import co.postscriptum.email.EnvelopeType;
import co.postscriptum.internal.MyConfiguration;
import co.postscriptum.internal.Utils;
import co.postscriptum.job.EmailProcessor;
import co.postscriptum.model.bo.DataFactory;
import co.postscriptum.model.bo.Lang;
import co.postscriptum.model.bo.RequiredAction;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.security.RSAOAEPUtils;
import co.postscriptum.security.RequestMetadata;
import co.postscriptum.web.AuthenticationHelper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;

@Service
@Slf4j
public class AdminHelperService {

    @Autowired
    private MyConfiguration configuration;

    @Autowired
    private EmailProcessor emailProcessor;

    @Autowired
    private DB db;

    @Value("${admin.public_key_path}")
    private String adminPublicKeyPath;

    @Getter
    private PublicKey adminPublicKey;

    @PostConstruct
    public void init() throws IOException {
        adminPublicKey = RSAOAEPUtils.toPublicKey(RSAOAEPUtils.fromPem(FileUtils.readFileToString(new File(adminPublicKeyPath))));
    }

    public UserData createAdmin(String loginPassword) {

        User user = User.builder()
                        .uuid(Utils.randKey("UA"))
                        .active(true)
                        .tosAccepted(true)
                        .username(configuration.getAdminEmail())
                        .role(User.Role.admin)
                        .build();

        UserData userData = new UserData();

        userData.setInternal(DataFactory.newAdminUserInternal(loginPassword));
        userData.setUser(user);
        userData.setRequiredActions(new ArrayList<>());
        userData.setNotifications(new ArrayList<>());

        userData.getInternal().setLang(Lang.en);
        userData.getInternal().setCreationTime(System.currentTimeMillis());
        userData.getInternal().setScreenName("Admin");

        return userData;

    }

    public void sendToAdminContactForm(String from, String title, String userInquiry, RequestMetadata metadata) {

        String loggedUsername = null;
        if (AuthenticationHelper.isUserLogged()) {
            loggedUsername = AuthenticationHelper.requireLoggedUsername();
        }

        String content =
                "<pre>From:\n" + Utils.asSafeText(from) + "</pre>" +
                        (loggedUsername != null
                                ? "<pre>Logged user:\n" + loggedUsername + "</pre>"
                                : "") +
                        "<pre>Content:\n" + Utils.asSafeText(userInquiry) + "</pre>" +
                        "<pre>Metadata:\n" + metadata.getRequestDetails() + "</pre>";

        emailProcessor.enqueue(Envelope.builder()
                                       .type(EnvelopeType.CONTACT_FORM)
                                       .recipient(configuration.getAdminEmail())
                                       .title("User Inquiry: " + Utils.asSafeText(title))
                                       .msgHeader("User inquiry")
                                       .msgContent(content)
                                       .build());

    }

    private void sendToAdminRequiredAction(RequiredAction requiredAction) {

        log.info("send email to admin about RequiredAction uuid={}", requiredAction.getUuid());

        String content = "<pre>" + Utils.toJson(requiredAction) + "</pre>";

        emailProcessor.enqueue(Envelope.builder()
                                       .type(EnvelopeType.SYSTEM_TO_ADMIN)
                                       .recipient(configuration.getAdminEmail())
                                       .title("Action required: " + requiredAction.getType())
                                       .msgContent(content)
                                       .build());

    }

    public void addAdminRequiredAction(RequiredAction requiredAction) {
        log.info("add admin RequiredAction: {}", requiredAction);

        sendToAdminRequiredAction(requiredAction);

        db.withLoadedAccountByUsername(configuration.getAdminEmail(), account -> {
            account.getUserData().getRequiredActions().add(requiredAction);
        });

    }

    public void addAdminNotification(String message) {

        log.info("add admin Notification: {}", message);

        db.withLoadedAccountByUsername(configuration.getAdminEmail(), account -> {
            new UserDataHelper(account.getUserData()).addNotification(message);
        });

    }

}
