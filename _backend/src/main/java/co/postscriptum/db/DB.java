package co.postscriptum.db;

import co.postscriptum.exception.BadRequestException;
import co.postscriptum.exception.InternalException;
import co.postscriptum.fs.FS;
import co.postscriptum.internal.MyConfiguration;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.PaymentAddress;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.User.Role;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.security.AESGCMEncrypted;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import co.postscriptum.stk.ShortTimeKey;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isEmpty;

@Component
@Slf4j
public class DB {

    @Autowired
    private MyConfiguration configuration;

    @Autowired
    private FS fs;

    private List<Account> stub;

    private volatile int lastStubHashCode = 0;

    private SecretKey dbEncryptionKey;

    public void shutdown() {
        log.info("shutdown DB");
        try {
            for (Account account : stub) {

                try {
                    account.lock();
                    unloadAccount(account);
                } finally {
                    account.unlock();
                }

            }

            saveStub();
        } catch (IOException e) {
            log.error("Error occurred while shutting down DB", e);
            try {
                FileUtils.writeStringToFile(new java.io.File("stub.dump"), Utils.toJson(stub));
            } catch (Exception e1) {
                log.error("Error occurred while persisting STUB", e1);
            }
        }
    }

    @PostConstruct
    public void init() throws IOException, DecoderException {
        log.info("initialize DB");

        if (!isEmpty(configuration.getDbEncKey())) {
            log.info("db encryption is enabled");
            dbEncryptionKey = AESKeyUtils.toSecretKey(Hex.decodeHex(configuration.getDbEncKey().toCharArray()));
        } else {
            log.info("db encryption is disabled");
        }

    }

    public void loadDB() throws IOException {
        try {
            loadStub();
        } catch (IOException e) {
            log.warn("can't load STUB", e);
            stub = new CopyOnWriteArrayList<>();
            throw e;
        }
    }

    public Map<String, Object> getStats() {
        Map<String, Object> map = new HashMap<>();

        map.put("totalAccounts", stub.size());
        map.put("loadedAccounts", getLoadedAccounts().count());

        return map;
    }

    public Stream<Account> getUserUnloadedAccounts() {
        return stub.stream()
                   .filter(account -> account.getUserData().getUser().getRole() == Role.user && !account.isLoaded());
    }

    public Stream<Account> getLoadedAccounts() {
        return stub.stream()
                   .filter(Account::isLoaded);
    }

    private Optional<Account> getAccount(Predicate<Account> filter) {
        return stub.stream()
                   .filter(filter)
                   .findFirst();
    }

    private boolean isPaymentAddressUnused(PaymentAddress paymentAddress) {
        //after 30 minutes, unloaded user's paymentAddress can be reused
        return paymentAddress != null &&
                paymentAddress.getAssignedTime() + Utils.minutesInMs(30) < System.currentTimeMillis();
    }

    private boolean isPaymentAddress(PaymentAddress paymentAddress, String paymentUuid) {
        return paymentAddress != null && paymentAddress.getUuid().equals(paymentUuid);
    }

    public Optional<Account> getAccountWithAssignedPayment(String paymentUuid) {
        return getAccount(a -> isPaymentAddress(a.getUserData().getUser().getPaymentAddress(), paymentUuid));
    }

    public Optional<PaymentAddress> harvestPaymentAddressFromUnloadedAccounts() {

        Optional<Account> accountOpt = getUserUnloadedAccounts()
                .filter(a -> isPaymentAddressUnused(a.getUserData().getUser().getPaymentAddress()))
                .findAny();

        if (!accountOpt.isPresent()) {
            return Optional.empty();
        }

        Account account = accountOpt.get();

        try {
            account.lock();

            User user = account.getUserData().getUser();

            PaymentAddress paymentAddress = user.getPaymentAddress();

            user.setPaymentAddress(null);

            return Optional.of(paymentAddress);

        } finally {
            account.unlock();
        }

    }

    public Account requireAccountByUuid(String uuid) {
        return getAccount(a -> a.getUserData().getUser().getUuid().equals(uuid))
                .orElseThrow(() -> new IllegalArgumentException("can't find account with uuid: " + uuid));
    }

    public Optional<Account> getAccountByUsername(String username) {
        return getAccount(a -> a.getUserData().getUser().getUsername().equals(username));
    }

    public Account requireAccountByUsername(String username) {
        return getAccount(a -> a.getUserData().getUser().getUsername().equals(username))
                .orElseThrow(() -> new IllegalArgumentException("can't find account with username: " + username));
    }

    public boolean hasAccountByUsername(String username) {
        return getAccount(a -> a.getUserData().getUser().getUsername().equals(username))
                .isPresent();
    }

    public void withLoadedAccount(Optional<Account> account, Consumer<Account> func) {
        withLoadedAccount(account.get(), func);
    }

    public void withLoadedAccount(Account account, Consumer<Account> func) {
        withLoadedAccount(account, account1 -> {
            func.accept(account1);
            return null;
        });

    }

    public <R> R withLoadedAccount(Account account, Function<Account, R> func) {

        try {
            account.lock();
            loadAccount(account);
            return func.apply(account);
        } finally {
            account.unlock();
        }

    }

    public void withLoadedAccountByUuid(String userUuid, Consumer<Account> func) {
        withLoadedAccount(requireAccountByUuid(userUuid), func);
    }

    public <R> R withLoadedAccountByShortTimeKey(ShortTimeKey stk, Function<Account, R> func) {
        return withLoadedAccountByUsername(stk.getUsername(), func);
    }

    public void withLoadedAccountByShortTimeKey(ShortTimeKey stk, Consumer<Account> func) {
        withLoadedAccount(requireAccountByUsername(stk.getUsername()), func);
    }

    public <R> R withLoadedAccountByUuid(String userUuid, Function<Account, R> func) {
        return withLoadedAccount(requireAccountByUuid(userUuid), func);
    }

    public <R> R withLoadedAccountByUsername(String username, Function<Account, R> func) {
        return withLoadedAccount(requireAccountByUsername(username), func);
    }

    public void withLoadedAccountByUsername(String username, Consumer<Account> func) {
        withLoadedAccount(requireAccountByUsername(username), func);
    }

    public void unloadUserByUuid(String uuid) throws IOException {
        unloadAccount(requireAccountByUuid(uuid));
    }

    public Account insertUser(UserData userData) throws IOException {

        String username = userData.getUser().getUsername();

        log.info("adding user: {}", username);

        Account account = new Account(userData);
        account.setLoaded(true);
        account.setLastAccessTime(System.currentTimeMillis());

        if (hasAccountByUsername(username)) {
            throw new BadRequestException("user already exists");
        }

        persistUser(userData);

        stub.add(account);

        return account;
    }

    public void removeAccount(Account account) {

        account.assertLockIsHeldByCurrentThread();

        stub.remove(account);

        loadAccount(account);

        UserData userData = account.getUserData();

        log.info("removing user {}", userData.getUser().getUsername());

        userData.getFiles().forEach(f -> {
            try {
                fs.remove(userData.getUser().getUuid() + "/" + f.getUuid());
            } catch (IOException e) {
                log.error("problem with removing user file", e);
            }
        });
        try {
            fs.remove(userData.getUser().getUuid() + "/db.json");
        } catch (IOException e) {
            log.error("problem with removing db user", e);
        }
        try {
            fs.remove(userData.getUser().getUuid());
        } catch (IOException e) {
            log.error("problem with removing user file directory", e);
        }
    }

    public void removeUserByUuid(String uuid) {
        removeAccount(requireAccountByUuid(uuid));
    }

    private String loadData(String dbPath) throws IOException {

        try (Reader fr = new InputStreamReader(fs.load(dbPath), StandardCharsets.UTF_8)) {
            if (dbEncryptionKey == null) {
                return IOUtils.toString(fr);
            }

            AESGCMEncrypted encrypted = Utils.fromJson(fr, new TypeReference<AESGCMEncrypted>() {
            });

            return Utils.asString(AESGCMUtils.decrypt(dbEncryptionKey, encrypted, dbPath));
        }

    }

    private void saveData(String dataJson, String dbPath) throws IOException {

        if (dbEncryptionKey == null) {
            fs.save(dbPath, dataJson);

        } else {
            AESGCMEncrypted encrypted = AESGCMUtils.encrypt(dbEncryptionKey, dataJson.getBytes(), dbPath);
            fs.save(dbPath, Utils.toJson(encrypted));
        }

    }

    public void loadAccount(Account account) {

        account.assertLockIsHeldByCurrentThread();

        account.setLastAccessTime(System.currentTimeMillis());
        if (account.isLoaded()) {
            return;
        }

        log.info("model for user " + account.getUserData().getUser().getUsername() + " is not loaded, loading it");

        String dbPath = getUserDBPath(account.getUserData().getUser());

        UserData ud;
        try {
            ud = Utils.fromJson(loadData(dbPath), new TypeReference<UserData>() {
            });
        } catch (IOException e) {
            throw new InternalException("can't load user model from db", e);
        }

        account.getUserData().setInternal(ud.getInternal());
        account.getUserData().setMessages(ud.getMessages());
        account.getUserData().setFiles(ud.getFiles());
        account.getUserData().setNotifications(ud.getNotifications());
        account.getUserData().setRequiredActions(ud.getRequiredActions());

        account.setLoaded(true);
        log.info("user model has been loaded");

    }

    public void unloadAccount(Account account) throws IOException {

        account.assertLockIsHeldByCurrentThread();

        if (!account.isLoaded()) {
            return;
        }

        log.info("unloading user {}", account.getUserData().getUser().getUsername());

        persistUser(account.getUserData());

        account.getUserData().setInternal(null);
        account.getUserData().setMessages(null);
        account.getUserData().setFiles(null);
        account.getUserData().setNotifications(null);
        account.getUserData().setRequiredActions(null);

        account.setLoaded(false);
        log.info("user has been unloaded");

    }

    private String getUserDBPath(User user) {
        return user.getUuid() + "/db.json";
    }

    private void persistUser(UserData userData) throws IOException {
        if (userData.getInternal() == null) {
            throw new IllegalArgumentException("shouldn't persist user when unloaded");
        }
        String userDBPath = getUserDBPath(userData.getUser());

        log.info("persisting user {} to {}", userData.getUser().getUsername(), userDBPath);

        String userDataJson = Utils.toJson(userData);

        if (userDataJson.length() > 100000) {
            log.warn("user {} model size is over 100kb", userData.getUser().getUsername());
        }

        saveData(userDataJson, userDBPath);

    }

    private String getStubPath() {
        return "stub.json";
    }

    public void saveStub() throws IOException {

        List<User> users = stub.stream()
                               .map(a -> a.getUserData().getUser())
                               .collect(Collectors.toList());

        String jsonStub = Utils.toJson(users);

        int stubHashCode = jsonStub.hashCode();

        if (lastStubHashCode != stubHashCode) {
            log.info("saving stub");

            saveData(jsonStub, getStubPath());

            lastStubHashCode = stubHashCode;

        }

    }

    private void loadStub() throws IOException {

        log.info("loading stub");

        List<User> users = Utils.fromJson(loadData(getStubPath()), new TypeReference<List<User>>() {
        });

        stub = new CopyOnWriteArrayList<>(
                users.stream().map(user -> {
                    UserData userData = new UserData();
                    userData.setUser(user);

                    Account account = new Account(userData);
                    account.setLoaded(false);
                    return account;
                }).collect(Collectors.toList()));

    }

}
