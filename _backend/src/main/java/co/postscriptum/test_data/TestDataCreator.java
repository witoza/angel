package co.postscriptum.test_data;

import co.postscriptum.internal.FileEncryptionService;
import co.postscriptum.internal.MessageContentUtils;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.DataFactory;
import co.postscriptum.model.bo.File;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.Message.Type;
import co.postscriptum.model.bo.PasswordEncryption;
import co.postscriptum.model.bo.ReleaseItem;
import co.postscriptum.model.bo.Trigger;
import co.postscriptum.model.bo.TriggerInternal;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.bo.UserInternal;
import co.postscriptum.security.AESGCMEncryptedByPassword;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import co.postscriptum.service.MessageReleaseService;
import co.postscriptum.service.UserDataHelper;
import co.postscriptum.service.UserService;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static co.postscriptum.internal.MessageContentUtils.buildMessageContent;
import static co.postscriptum.internal.MessageContentUtils.getMessageContent;
import static co.postscriptum.internal.Utils.asArrayList;
import static java.util.Arrays.asList;

@Component
public class TestDataCreator {

    @Autowired
    private FileEncryptionService fileEncryptionService;

    @Value("${test.data.dir}")
    private String testDataDir;

    private String xssUtfContent =
            "\nXSS = <body onload=alert('test1')>" +
                    "<b onmouseover=alert('Wufff!')>click me!</b>" +
                    "<img src='http://url.to.file.which/not.exist' onerror=alert(document.cookie);>" +
                    "\nUTF = <h1>ąśćżźłó</h1>";

    private final TestUser janeFirst = new TestUser() {

        @Override
        public User createUser() {
            User user = DataFactory.newUser();
            user.setActive(true);
            user.setTosAccepted(false);
            user.setUsername("jane.first@postscriptum.co");
            user.setUuid("user-" + user.getUsername());

            return user;
        }

        @Override
        public UserData createUserData(User user, PublicKey adminPublicKey) {
            final String loginPassword = "passwd2";

            Trigger trigger = user.getTrigger();
            trigger.setEnabled(true);
            trigger.setTimeUnit(ChronoUnit.DAYS);
            trigger.setX(30);
            trigger.setY(15);
            trigger.setZ(10);
            trigger.setW(5);

            UserData userData = DataFactory.newUserData(
                    user, DataFactory.newUserInternal(loginPassword, adminPublicKey));
            userData.getInternal().setQuotaBytes(1024 * 1024 * 50);

            //now Jane has to extend the account
            userData.getInternal().getUserPlan().setPaidUntil(System.currentTimeMillis());

            fixUserData(userData);
            return userData;
        }
    };

    private final TestUser jackEmpty = new TestUser() {

        @Override
        public User createUser() {
            User user = DataFactory.newUser();
            user.setActive(true);
            user.setTosAccepted(false);
            user.setUsername("jack.empty@postscriptum.co");
            user.setUuid("user-" + user.getUsername());

            return user;
        }

        @Override
        public UserData createUserData(User user, PublicKey adminPublicKey) {
            final String loginPassword = "passwd3";

            Trigger trigger = user.getTrigger();
            trigger.setEnabled(true);
            trigger.setTimeUnit(ChronoUnit.DAYS);
            trigger.setX(30);
            trigger.setY(15);
            trigger.setZ(10);
            trigger.setW(5);

            UserData userData = DataFactory.newUserData(
                    user, DataFactory.newUserInternal(loginPassword, adminPublicKey));
            userData.getInternal().setQuotaBytes(500);

            fixUserData(userData);
            return userData;
        }
    };

    private final TestUser bobNonactive = new TestUser() {

        @Override
        public User createUser() {
            User user = DataFactory.newUser();
            user.setActive(false);
            user.setTosAccepted(false);
            user.setUsername("bob.nonactive@postscriptum.co");
            user.setUuid("user-" + user.getUsername());

            return user;
        }

        @Override
        public UserData createUserData(User user, PublicKey adminPublicKey) {
            final String loginPassword = "passwd2";

            Trigger trigger = user.getTrigger();
            trigger.setEnabled(false);

            UserData userData = DataFactory.newUserData(
                    user, DataFactory.newUserInternal(loginPassword, adminPublicKey));

            userData.getInternal().setQuotaBytes(1024 * 1024 * 50);

            fixUserData(userData);
            return userData;
        }
    };

    private final TestUser johnData = new TestUser() {

        @Override
        public User createUser() {
            User user = DataFactory.newUser();
            user.setActive(true);
            user.setTosAccepted(true);
            user.setUsername("john+data@postscriptum.co");
            user.setUuid("user-" + user.getUsername());

            return user;
        }

        private long lastXMinutes(long minutes) {
            return System.currentTimeMillis() - Utils.minutesToMillis(minutes);
        }

        @Override
        public UserData createUserData(User user, PublicKey adminPublicKey) throws IOException {
            final String loginPassword = "passwd";

            UserInternal internal = DataFactory.newUserInternal(loginPassword, adminPublicKey);

            SecretKey encryptionKey =
                    AESKeyUtils.toSecretKey(AESGCMUtils.decryptByPassword(loginPassword, internal.getEncryptionKey()));

            Trigger trigger = user.getTrigger();
            trigger.setEnabled(true);
            trigger.setTimeUnit(ChronoUnit.DAYS);
            trigger.setX(30);
            trigger.setY(15);
            trigger.setZ(10);
            trigger.setW(5);

            File f1 = File.builder()
                          .name("info_treasure.docx")
                          .mime("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                          .build();

            File f2 = File.builder()
                          .name("my_accounts.xlsx")
                          .mime("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                          .build();

            File f4 = File.builder()
                          .name("train_by_day.mp4")
                          .mime("video/mp4")
                          .build();

            File f5 = File.builder()
                          .name("Richard, hello for you sir !.webm")
                          .mime("video/webm")
                          .build();

            File f6 = File.builder()
                          .name("treasure_map.jpg")
                          .mime("image/jpg")
                          .build();

            File f7 = File.builder()
                          .name("top_secret_doc#123442.jpg")
                          .mime("image/jpg")
                          .build();

            for (File file : asArrayList(f1, f2, f4, f5, f6, f7)) {
                fixFile(user, file, encryptionKey);
            }

            Message m0 = DataFactory.newMessage();
            m0.setType(Type.outbox);
            m0.setCreationTime(lastXMinutes(1));
            m0.setTitle("XSS and UTF test_data");
            m0.setRecipients(asArrayList("test_data@test_data.co"));
            m0.setContent(buildMessageContent(m0, encryptionKey, xssUtfContent));

            Message m1 = DataFactory.newMessage();
            m1.setType(Type.outbox);
            m1.setCreationTime(lastXMinutes(60));
            m1.setTitle("I need to get this off my chest");
            m1.setRecipients(asArrayList("luke.skywalker@postscriptum.co", "joda@postscriptum.co", "tsubasa"));
            m1.setContent(buildMessageContent(m1,
                                              encryptionKey,
                                              "Hi Luke<br/><br/>I know this is hard but I have to tell you.... I am your father.</br></br><b>Dark Vader</b>"));

            for (String recipient : m1.getRecipients()) {
                ReleaseItem ri = MessageReleaseService.createReleaseItem(m1, recipient);
                ri.setKey("witoza");
            }

            Message m2 = DataFactory.newMessage();
            m2.setType(Type.outbox);
            m2.setCreationTime(lastXMinutes(24 * 60));
            m2.setTitle("My all assets");
            m2.setRecipients(asArrayList("mr.obama@postscriptum.co", "mr.obama2@postscriptum.co"));
            m2.setAttachments(asArrayList(f2.getUuid()));
            m2.setContent(buildMessageContent(m2,
                                              encryptionKey,
                                              "Yo Barack, Take it all, dude - pro publico bono!"));
            setPassword(m2, encryptionKey, "knuth", "Surname of a Mr. Donald, from The Art of Computer Programming");

            File encF1 = fileEncryptionService.encryptFileByPassword(f1, user, "knuth");
            encF1.setBelongsTo(m2.getUuid());
            m2.getAttachments().add(encF1.getUuid());


            Message m3 = DataFactory.newMessage();
            m3.setType(Type.drafts);
            m3.setCreationTime(System.currentTimeMillis());
            m3.setTitle("Important video (first draft)");
            m3.setRecipients(asArrayList("jane.smith@postscriptum.co"));
            m3.setContent(buildMessageContent(m3,
                                              encryptionKey,
                                              "Hi Jane, check that out: https://www.youtube.com/watch?v=w3gODO5vU_M, its a video of my dog !"));
            setPassword(m3, encryptionKey, "dog", "Nora, family's pet is a cat or dog? ");

            Message m4 = DataFactory.newMessage();
            m4.setType(Type.drafts);
            m4.setCreationTime(lastXMinutes(30 * 24 * 60));
            m4.setTitle("Important video (second draft)");
            m4.setRecipients(asArrayList("jane.smith@postscriptum.co"));
            m4.setContent(buildMessageContent(m4,
                                              encryptionKey,
                                              "Hi Jane, check that out: https://www.youtube.com/watch?v=w3gODO5vU_M, its a video of my cat and dog ! Please share it !"));
            setPassword(m4, encryptionKey, "kosiek", "name of a math teacher is high school");

            Message m5 = DataFactory.newMessage();
            m5.setType(Type.outbox);
            m5.setCreationTime(lastXMinutes(60 * 24 * 60));
            m5.setTitle("Joe! Greetings from hell");
            m5.setRecipients(asArrayList("joe.rogan@postscriptum.co"));
            m5.setAttachments(asArrayList(f4.getUuid()));
            m5.setContent(buildMessageContent(m5,
                                              encryptionKey,
                                              "<h1>Train by day, Joe Rogan podcast by night!</h1> <br/>https://www.youtube.com/watch?v=7XZJiE5Ro4U"));

            Message m6 = DataFactory.newMessage();
            m6.setType(Type.drafts);
            m6.setCreationTime(lastXMinutes(356 * 24 * 60));
            m6.setTitle("Richard! Greetings from hell");
            m6.setRecipients(asArrayList("mr.richard.dawkins@postscriptum.co"));
            m6.setAttachments(asArrayList(f5.getUuid(), f6.getUuid(), f7.getUuid()));
            m6.setContent(buildMessageContent(m6,
                                              encryptionKey,
                                              "Hi, This is my getLast will and you\'d better respect that Richard you sick f***"));

            Message m7 = DataFactory.newMessage();
            m7.setType(Type.drafts);
            m7.setCreationTime(lastXMinutes(1000 * 24 * 60));
            m7.setTitle("John! Greetings from hell");
            m7.setRecipients(asArrayList("lohn.lenon@postscriptum.co"));
            m7.setAttachments(asArrayList(f5.getUuid(), f6.getUuid(), f7.getUuid()));
            m7.setContent(buildMessageContent(m7,
                                              encryptionKey,
                                              "Imagine there's no heaven<br/>It's easy if you try<br/>No hell below us<br/>Above us only sky<br/><br/>Imagine all the people<br/>Living for today<br/>Aha-ahh<br/><br/>Imagine there's no countries<br/>It isn't hard to do<br/>Nothing to kill or die for<br/>And no religion too<br/><br/>Imagine all the people<br/>Living life in peace<br/>Yoohoo-ooh<br/><br/>You may say I'm a dreamer<br/>But I'm not the only one<br/>I hope someday you'll join us<br/>And the world will be as one<br/><br/>Imagine no possessions<br/>I wonder if you can<br/>No need for greed or hunger<br/>A brotherhood of man<br/><br/>Imagine all the people<br/>Sharing all the world<br/>Yoohoo-ooh<br/><br/>You may say I'm a dreamer<br/>But I'm not the only one<br/>I hope someday you'll join us<br/>And the world will live as one"));

            UserData userData = DataFactory.newUserData(user, internal);
            userData.setFiles(asArrayList(f1, encF1, f2, f4, f5, f6, f7));
            userData.setMessages(asArrayList(m0, m1, m2, m3, m4, m5, m6, m7));
            // never has to pay
            userData.getInternal().getUserPlan().setPaidUntil(System.currentTimeMillis() + Utils.daysInMs(10000));
            userData.getInternal().setQuotaBytes(1024 * 1024 * 50);
            fixUserData(userData);
            return userData;
        }

    };

    private void fixFile(User user, File file, SecretKey encryptionKey) throws IOException {

        String fromFileUrl = testDataDir + file.getName();

        file.setUploadTime(System.currentTimeMillis());
        file.setUuid(Utils.randKey("F"));
        file.setExt(FilenameUtils.getExtension(fromFileUrl));

        fileEncryptionService.saveStreamToFile(file, user, encryptionKey, new FileInputStream(fromFileUrl));

    }

    private void setPassword(Message message, SecretKey encryptionKey, String password, String hint) {
        String plaintextContent = getMessageContent(message, encryptionKey, null);

        AESGCMEncryptedByPassword passwordEncrypted = AESGCMUtils.encryptByPassword(password, plaintextContent.getBytes());

        message.setContent(MessageContentUtils.buildMessageContent(message,
                                                                   encryptionKey,
                                                                   passwordEncrypted.getEncrypted().getCt()));

        message.setEncryption(PasswordEncryption.builder()
                                                .salt(passwordEncrypted.getPasswordSalt())
                                                .hint(hint)
                                                .iv(passwordEncrypted.getEncrypted().getIv())
                                                .build());

    }

    private void fixUserData(UserData userData) {

        userData.getInternal().setVerifyUnknownBrowsers(false);

        new UserDataHelper(userData).addNotification("Test input model: " + xssUtfContent);
        userData.getInternal().setTotpRecoveryEmail(userData.getUser().getUsername());
        userData.getInternal().setScreenName(UserService.guessScreenName(userData.getUser().getUsername()));

        TriggerInternal triggerInternal = userData.getInternal().getTriggerInternal();
        triggerInternal.setXemails(userData.getUser().getUsername());
        triggerInternal.setYemails("me_other@postscriptum.co");
        triggerInternal.setZemails("my_friend@postscriptum.co");
    }

    public List<TestUser> getTestUsers() {
        return asList(bobNonactive, jackEmpty, janeFirst, johnData);
    }

}
