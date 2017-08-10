package co.postscriptum.test_data;

import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.DataFactory;
import co.postscriptum.model.bo.File;
import co.postscriptum.model.bo.Message;
import co.postscriptum.model.bo.Message.Type;
import co.postscriptum.model.bo.ReleaseItem;
import co.postscriptum.model.bo.Trigger;
import co.postscriptum.model.bo.TriggerInternal;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.bo.UserInternal;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import co.postscriptum.service.FileService;
import co.postscriptum.service.MessageService;
import co.postscriptum.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.PublicKey;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

@Component
public class TestDataCreator {

    @Autowired
    private MessageService messageService;

    @Autowired
    private FileService fileService;

    @Value("${test.data.dir}")
    private String testDataDir;

    private String xssUtfContent =
            "\nXSS = <body onload=alert('test1')>" +
                    "<b onmouseover=alert('Wufff!')>click me!</b>" +
                    "<img src='http://url.to.file.which/not.exist' onerror=alert(document.cookie);>" +
                    "\nUTF = <h1>ąśćżźłó</h1>";

    private final TestUser janeFirst = new TestUser() {

        @Override
        public String getUsername() {
            return "jane.first@postscriptum.co";
        }

        @Override
        public UserData createUserData(PublicKey adminPublicKey) {
            final String loginPassword = "passwd2";

            User user = DataFactory.newUser();
            user.setActive(true);
            user.setTosAccepted(false);
            user.setUsername(getUsername());
            user.setUuid("user-" + user.getUsername());

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

            setDefaultsUserData(userData);
            return userData;
        }
    };

    private final TestUser jackEmpty = new TestUser() {

        @Override
        public String getUsername() {
            return "jack.empty@postscriptum.co";
        }

        @Override
        public UserData createUserData(PublicKey adminPublicKey) {
            final String loginPassword = "passwd3";

            User user = DataFactory.newUser();
            user.setActive(true);
            user.setTosAccepted(false);
            user.setUsername(getUsername());
            user.setUuid("user-" + user.getUsername());

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

            setDefaultsUserData(userData);
            return userData;
        }
    };

    private final TestUser bobNonactive = new TestUser() {

        @Override
        public String getUsername() {
            return "bob.nonactive@postscriptum.co";
        }


        @Override
        public UserData createUserData(PublicKey adminPublicKey) {
            final String loginPassword = "passwd2";

            User user = DataFactory.newUser();
            user.setActive(false);
            user.setTosAccepted(false);
            user.setUsername(getUsername());
            user.setUuid("user-" + user.getUsername());

            Trigger trigger = user.getTrigger();
            trigger.setEnabled(false);

            UserData userData = DataFactory.newUserData(user, DataFactory.newUserInternal(loginPassword, adminPublicKey));

            userData.getInternal().setQuotaBytes(1024 * 1024 * 50);

            setDefaultsUserData(userData);
            return userData;
        }
    };

    private final TestUser johnData = new TestUser() {

        @Override
        public String getUsername() {
            return "john+data@postscriptum.co";
        }

        private long lastXMinutes(long minutes) {
            return System.currentTimeMillis() - Utils.minutesToMillis(minutes);
        }

        @Override
        public UserData createUserData(PublicKey adminPublicKey) throws IOException {
            final String loginPassword = "passwd";

            User user = DataFactory.newUser();
            user.setActive(true);
            user.setTosAccepted(true);
            user.setUsername(getUsername());
            user.setUuid("user-" + user.getUsername());

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

            UserData userData = DataFactory.newUserData(user, internal);
            // never has to pay
            userData.getInternal().getUserPlan().setPaidUntil(System.currentTimeMillis() + Utils.daysToMillis(10000));
            userData.getInternal().setQuotaBytes(1024 * 1024 * 50);

            File f1 = fileService.upload(userData, encryptionKey, new SimpleMultipartFile(testDataDir, "info_treasure.docx"), null);
            File f2 = fileService.upload(userData, encryptionKey, new SimpleMultipartFile(testDataDir, "my_accounts.xlsx"), null);
            File f4 = fileService.upload(userData, encryptionKey, new SimpleMultipartFile(testDataDir, "train_by_day.mp4"), null);
            File f5 = fileService.upload(userData, encryptionKey, new SimpleMultipartFile(testDataDir, "Richard, hello for you sir !.webm"), "My recording");
            File f6 = fileService.upload(userData, encryptionKey, new SimpleMultipartFile(testDataDir, "treasure_map.jpg"), null);
            File f7 = fileService.upload(userData, encryptionKey, new SimpleMultipartFile(testDataDir, "top_secret_doc#123442.jpg"), null);

            Message msg0 = messageService.addMessage(userData, encryptionKey,
                                                     Type.outbox,
                                                     Collections.singletonList("test_data@test_data.co"),
                                                     Collections.emptyList(),
                                                     "XSS and UTF test_data",
                                                     xssUtfContent);
            msg0.setCreationTime(lastXMinutes(5));

            Message msg1 = messageService.addMessage(userData, encryptionKey,
                                                     Type.outbox,
                                                     Arrays.asList("luke.skywalker@postscriptum.co", "joda@postscriptum.co", "tsubasa"),
                                                     Collections.emptyList(),
                                                     "I need to get this off my chest",
                                                     "Hi Luke<br/><br/>I know this is hard but I have to tell you.... I am your father.</br></br><b>Dark Vader</b>");
            msg1.setCreationTime(lastXMinutes(60));

            for (String recipient : msg1.getRecipients()) {
                ReleaseItem ri = msg1.addReleaseItem(recipient);
                ri.setKey("witoza");
            }

            Message msg2 = messageService.addMessage(userData, encryptionKey,
                                                     Type.outbox,
                                                     Arrays.asList("mr.obama@postscriptum.co", "mr.obama2@postscriptum.co"),
                                                     Arrays.asList(f1.getUuid(), f2.getUuid()),
                                                     "My all assets",
                                                     "Yo Barack, Take it all, dude - pro publico bono!");
            msg2.setCreationTime(lastXMinutes(24 * 60));

            messageService.setPassword(userData, encryptionKey, msg2.getUuid(), null, "knuth", "Surname of a Mr. Donald, from The Art of Computer Programming");
            fileService.encrypt(userData, msg2.getUuid(), f2.getUuid(), "knuth");

            Message msg3 = messageService.addMessage(userData, encryptionKey,
                                                     Type.drafts,
                                                     Collections.singletonList("jane.smith@postscriptum.co"),
                                                     Collections.emptyList(),
                                                     "Important video (first draft)",
                                                     "Hi Jane, check that out: https://www.youtube.com/watch?v=w3gODO5vU_M, its a video of my dog !");

            messageService.setPassword(userData, encryptionKey, msg3.getUuid(), null, "dog", "Nora, family's pet is a cat or dog?");

            Message msg4 = messageService.addMessage(userData, encryptionKey,
                                                     Type.drafts,
                                                     Collections.singletonList("jane.smith@postscriptum.co"),
                                                     Collections.emptyList(),
                                                     "Important video (second draft)",
                                                     "Hi Jane, check that out: https://www.youtube.com/watch?v=w3gODO5vU_M, its a video of my cat and dog ! Please share it !");
            msg4.setCreationTime(lastXMinutes(30 * 24 * 60));
            messageService.setPassword(userData, encryptionKey, msg4.getUuid(), null, "kosiek", "Name of a math teacher is high school");


            Message msg5 = messageService.addMessage(userData, encryptionKey,
                                                     Type.outbox,
                                                     Collections.singletonList("joe.rogan@postscriptum.co"),
                                                     Collections.singletonList(f4.getUuid()),
                                                     "Joe! Greetings from hell",
                                                     "<h1>Train by day, Joe Rogan podcast by night!</h1> <br/>https://www.youtube.com/watch?v=7XZJiE5Ro4U");
            msg5.setCreationTime(lastXMinutes(60 * 24 * 60));

            Message msg6 = messageService.addMessage(userData, encryptionKey,
                                                     Type.drafts,
                                                     Collections.singletonList("mr.richard.dawkins@postscriptum.co"),
                                                     Arrays.asList(f5.getUuid(), f6.getUuid(), f7.getUuid()),
                                                     "Richard! Greetings from hell",
                                                     "Hi, This is my getLast will and you\'d better respect that Richard you sick f***");
            msg6.setCreationTime(lastXMinutes(356 * 24 * 60));

            Message msg7 = messageService.addMessage(userData, encryptionKey,
                                                     Type.drafts,
                                                     Collections.singletonList("lohn.lenon@postscriptum.co"),
                                                     Arrays.asList(f5.getUuid(), f6.getUuid(), f7.getUuid()),
                                                     "John! Greetings from hell",
                                                     "Imagine there's no heaven<br/>It's easy if you try<br/>No hell below us<br/>Above us only sky<br/><br/>Imagine all the people<br/>Living for today<br/>Aha-ahh<br/><br/>Imagine there's no countries<br/>It isn't hard to do<br/>Nothing to kill or die for<br/>And no religion too<br/><br/>Imagine all the people<br/>Living life in peace<br/>Yoohoo-ooh<br/><br/>You may say I'm a dreamer<br/>But I'm not the only one<br/>I hope someday you'll join us<br/>And the world will be as one<br/><br/>Imagine no possessions<br/>I wonder if you can<br/>No need for greed or hunger<br/>A brotherhood of man<br/><br/>Imagine all the people<br/>Sharing all the world<br/>Yoohoo-ooh<br/><br/>You may say I'm a dreamer<br/>But I'm not the only one<br/>I hope someday you'll join us<br/>And the world will live as one");
            msg7.setCreationTime(lastXMinutes(1000 * 24 * 60));

            setDefaultsUserData(userData);
            return userData;
        }

    };

    private void setDefaultsUserData(UserData userData) {

        userData.getInternal().setVerifyUnknownBrowsers(false);

        userData.addNotification("Test input model: " + xssUtfContent);
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
