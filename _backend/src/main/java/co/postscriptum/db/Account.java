package co.postscriptum.db;

import co.postscriptum.exception.InternalException;
import co.postscriptum.model.bo.UserData;
import lombok.Synchronized;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Account {

    private final UserData userData;

    private final ReentrantLock lock = new ReentrantLock();

    private long lastAccessTime;

    private boolean loaded;

    private boolean toBeRemoved;

    public Account(UserData userData) {
        this.userData = userData;
    }

    public void assertLockIsHeldByCurrentThread() {
        if (!lock.isHeldByCurrentThread()) {
            throw new InternalException("Account lock must be held by this thread");
        }
    }

    public void lock() {
        try {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                throw new InternalException("Can't lock Account for 5 seconds");
            }
        } catch (InterruptedException e) {
            throw new InternalException("Waiting for Account lock has been interrupted", e);
        }

    }

    public void unlock() {
        lock.unlock();
    }

    @Synchronized
    public long getLastAccessTime() {
        return lastAccessTime;
    }

    @Synchronized
    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    @Synchronized
    public boolean isLoaded() {
        return loaded;
    }

    @Synchronized
    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    @Synchronized
    public boolean isToBeRemoved() {
        return toBeRemoved;
    }

    @Synchronized
    public void setToBeRemoved(boolean toBeRemoved) {
        this.toBeRemoved = toBeRemoved;
    }

    public UserData getUserData() {
        return userData;
    }

    public void loadUserData(UserData fromUserData) {
        if (isLoaded()) {
            throw new InternalException("Can't load to loaded user");
        }
        userData.setInternal(fromUserData.getInternal());
        userData.setMessages(fromUserData.getMessages());
        userData.setFiles(fromUserData.getFiles());
        userData.setNotifications(fromUserData.getNotifications());
        userData.setRequiredActions(fromUserData.getRequiredActions());
        setLoaded(true);
    }

    public void unloadUserData() {
        if (!isLoaded()) {
            throw new InternalException("Can't unload from unloaded user");
        }
        userData.setInternal(null);
        userData.setMessages(null);
        userData.setFiles(null);
        userData.setNotifications(null);
        userData.setRequiredActions(null);
        setLoaded(false);
    }

}
