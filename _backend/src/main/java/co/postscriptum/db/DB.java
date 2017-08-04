package co.postscriptum.db;

import co.postscriptum.exception.BadRequestException;
import co.postscriptum.exception.InternalException;
import co.postscriptum.fs.FS;
import co.postscriptum.internal.MyConfiguration;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.User.Role;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.security.AESGCMEncrypted;
import co.postscriptum.security.AESGCMUtils;
import co.postscriptum.security.AESKeyUtils;
import co.postscriptum.stk.ShortTimeKey;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
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

    @PostConstruct
    public void init() throws IOException, DecoderException {
        log.info("Initialize DB");

        if (!isEmpty(configuration.getDbEncKey())) {
            log.info("DB encryption is enabled");
            dbEncryptionKey = AESKeyUtils.toSecretKey(Hex.decodeHex(configuration.getDbEncKey().toCharArray()));
        } else {
            log.info("DB encryption is disabled");
        }
    }

    public void shutdown() {
        log.info("Shutdown DB");
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
                log.error("Error occurred while persisting Stub", e1);
            }
        }
    }

    public void loadDb() throws IOException {
        try {
            loadStub();
        } catch (IOException e) {
            stub = new CopyOnWriteArrayList<>();
            throw e;
        }
    }

    public Map<String, Object> getStats() {
        return ImmutableMap.of(
                "totalAccounts", stub.size(),
                "loadedAccounts", getLoadedAccounts().count()
        );
    }

    public Stream<Account> getUserUnloadedAccounts() {
        return stub.stream()
                   .filter(account -> account.getUserData().getUser().getRole() == Role.user && !account.isLoaded());
    }

    public Stream<Account> getLoadedAccounts() {
        return stub.stream()
                   .filter(Account::isLoaded);
    }

    public Optional<Account> getAccount(Predicate<Account> filter) {
        return stub.stream()
                   .filter(filter)
                   .findFirst();
    }

    public Account requireAccountByUuid(String uuid) {
        return getAccount(a -> a.getUserData().getUser().getUuid().equals(uuid))
                .orElseThrow(() -> new IllegalArgumentException("Can't find account with uuid: " + uuid));
    }

    public Optional<Account> getAccountByUsername(String username) {
        return getAccount(a -> a.getUserData().getUser().getUsername().equals(username));
    }

    public Account requireAccountByUsername(String username) {
        return getAccount(a -> a.getUserData().getUser().getUsername().equals(username))
                .orElseThrow(() -> new IllegalArgumentException("Can't find account with username: " + username));
    }

    public boolean hasAccountByUsername(String username) {
        return getAccount(a -> a.getUserData().getUser().getUsername().equals(username))
                .isPresent();
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

    public <R> R withLoadedAccountByShortTimeKey(ShortTimeKey shortTimeKey, Function<Account, R> func) {
        return withLoadedAccountByUsername(shortTimeKey.getUsername(), func);
    }

    public void withLoadedAccountByShortTimeKey(ShortTimeKey shortTimeKey, Consumer<Account> func) {
        withLoadedAccount(requireAccountByUsername(shortTimeKey.getUsername()), func);
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

        log.info("Adding user: {}", username);

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

        log.info("Removing user {}", account.getUserData().getUser().getUsername());

        stub.remove(account);

        loadAccount(account);

        UserData userData = account.getUserData();

        userData.getFiles().forEach(f -> {
            try {
                fs.remove(userData.getUser().getUuid() + "/" + f.getUuid());
            } catch (IOException e) {
                log.error("Problem with removing user file", e);
            }
        });
        try {
            fs.remove(userData.getUser().getUuid() + "/db.json");
        } catch (IOException e) {
            log.error("Problem with removing db user", e);
        }
        try {
            fs.remove(userData.getUser().getUuid());
        } catch (IOException e) {
            log.error("Problem with removing user file directory", e);
        }
    }

    public void removeUserByUuid(String uuid) {
        removeAccount(requireAccountByUuid(uuid));
    }

    public void loadAccount(Account account) {

        account.assertLockIsHeldByCurrentThread();

        account.setLastAccessTime(System.currentTimeMillis());
        if (account.isLoaded()) {
            return;
        }

        log.info("Loading model for user {}", account.getUserData().getUser().getUsername());

        UserData userData = loadUserData(userDbPath(account.getUserData().getUser()));

        account.getUserData().setInternal(userData.getInternal());
        account.getUserData().setMessages(userData.getMessages());
        account.getUserData().setFiles(userData.getFiles());
        account.getUserData().setNotifications(userData.getNotifications());
        account.getUserData().setRequiredActions(userData.getRequiredActions());

        account.setLoaded(true);
        log.info("User's account has been loaded");
    }

    public void unloadAccount(Account account) throws IOException {

        account.assertLockIsHeldByCurrentThread();

        if (!account.isLoaded()) {
            return;
        }

        log.info("Unloading user {}", account.getUserData().getUser().getUsername());

        persistUser(account.getUserData());

        account.getUserData().setInternal(null);
        account.getUserData().setMessages(null);
        account.getUserData().setFiles(null);
        account.getUserData().setNotifications(null);
        account.getUserData().setRequiredActions(null);

        account.setLoaded(false);
        log.info("User has been unloaded");
    }

    private void persistUser(UserData userData) throws IOException {
        if (userData.getInternal() == null) {
            throw new IllegalArgumentException("shouldn't persist user when unloaded");
        }
        String userDbPath = userDbPath(userData.getUser());

        log.info("Persisting user: {} to: {}", userData.getUser().getUsername(), userDbPath);

        String userDataJson = Utils.toJson(userData);
        if (userDataJson.length() > 50000) {
            log.warn("User model size is > 50kb");
        }
        if (userDataJson.length() > 200000) {
            throw new InternalException("User " + userData.getUser().getUsername() + " model size is " + userDataJson.length());
        }
        saveData(userDataJson, userDbPath);
    }

    private String userDbPath(User user) {
        return user.getUuid() + "/db.json";
    }

    public void saveStub() throws IOException {
        List<User> users = stub.stream()
                               .map(a -> a.getUserData().getUser())
                               .collect(Collectors.toList());

        String stubJson = Utils.toJson(users);

        int stubHashCode = stubJson.hashCode();

        if (lastStubHashCode != stubHashCode) {
            log.info("Saving Stub");
            saveData(stubJson, getStubPath());
            lastStubHashCode = stubHashCode;
        }
    }

    private void loadStub() throws IOException {
        log.info("Loading Stub");

        List<User> users = Utils.fromJson(loadData(getStubPath()), new TypeReference<List<User>>() {
        });

        List<Account> accounts = users.stream()
                                      .map(user -> {
                                          UserData userData = new UserData();
                                          userData.setUser(user);

                                          Account account = new Account(userData);
                                          account.setLoaded(false);
                                          return account;
                                      }).collect(Collectors.toList());

        stub = new CopyOnWriteArrayList<>(accounts);
    }

    private String getStubPath() {
        return "stub.json";
    }

    private UserData loadUserData(String dbPath) {
        try {
            return Utils.fromJson(loadData(dbPath), new TypeReference<UserData>() {
            });
        } catch (IOException e) {
            throw new InternalException("Can't load user model from db", e);
        }
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

    private void saveData(String data, String dbPath) throws IOException {
        log.info("Persisting {} KB", data.length() / 1024);
        if (dbEncryptionKey == null) {
            fs.save(dbPath, data);
        } else {
            AESGCMEncrypted encrypted = AESGCMUtils.encrypt(dbEncryptionKey, data.getBytes(), dbPath);
            fs.save(dbPath, Utils.toJson(encrypted));
        }
    }

}
