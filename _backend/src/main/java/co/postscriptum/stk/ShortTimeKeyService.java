package co.postscriptum.stk;

import co.postscriptum.exception.ExceptionBuilder;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.UserData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ShortTimeKeyService {

    // Don't persist as they are not very important and user can always regenerate them
    private final List<ShortTimeKey> shortTimeKeys = new CopyOnWriteArrayList<>();

    public ShortTimeKey require(String key, ShortTimeKey.Type type) {
        return getByKey(key, type)
                .orElseThrow(ExceptionBuilder.missingClass(ShortTimeKey.class, "key=" + key));
    }

    public Optional<ShortTimeKey> getByKey(String key, ShortTimeKey.Type type) {
        removeOld();
        return shortTimeKeys.stream()
                            .filter(stk -> stk.getKey().equals(key) && stk.getType() == type)
                            .findAny();
    }

    public void removeKey(ShortTimeKey key) {
        shortTimeKeys.remove(key);
    }

    public ShortTimeKey create(UserData userData, ShortTimeKey.Type type) {
        return create(userData.getUser().getUsername(), type);
    }

    public ShortTimeKey create(String username, ShortTimeKey.Type type) {
        removeOld();

        ShortTimeKey shortTimeKey =
                shortTimeKeys.stream()
                             .filter(p -> p.getUsername().equals(username) && p.getType() == type)
                             .findAny()
                             .orElseGet(() -> {
                                 ShortTimeKey stk = ShortTimeKey.builder()
                                                                .username(username)
                                                                .key(Utils.randKey(""))
                                                                .type(type)
                                                                .build();
                                 shortTimeKeys.add(stk);
                                 return stk;
                             });

        shortTimeKey.setValidUntil(System.currentTimeMillis() + Utils.minutesToMillis(60));

        return shortTimeKey;
    }

    private void removeOld() {
        shortTimeKeys.removeIf(stk -> System.currentTimeMillis() > stk.getValidUntil());
    }

}
