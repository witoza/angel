package co.postscriptum.job;

import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.Trigger;
import co.postscriptum.model.bo.Trigger.Stage;
import com.google.common.collect.ImmutableMap;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public class TimeStages {

    private final LocalDateTime lastAccess;

    private final LocalDateTime Xdt;

    private final LocalDateTime Ydt;

    private final LocalDateTime Zdt;

    private final LocalDateTime Wdt;

    private final LocalDateTime now;

    public TimeStages(Trigger trigger, LocalDateTime now, long userLastAccessTimestamp) {

        ChronoUnit timeUnit = trigger.getTimeUnit();
        lastAccess = Utils.fromTimestamp(userLastAccessTimestamp);
        Xdt = lastAccess.plus(trigger.getX(), timeUnit);
        Ydt = Xdt.plus(trigger.getY(), timeUnit);
        Zdt = Ydt.plus(trigger.getZ(), timeUnit);
        Wdt = Zdt.plus(trigger.getW(), timeUnit);
        this.now = now;
    }

    public Map<String, String> debug() {
        return ImmutableMap.<String, String>builder()
                .put("now", Utils.format(now))
                .put("user.lastAccess", Utils.format(lastAccess))
                .put("Xdt", Utils.format(Xdt))
                .put("Ydt", Utils.format(Ydt))
                .put("Zdt", Utils.format(Zdt))
                .put("Wdt", Utils.format(Wdt))
                .build();
    }

    boolean nowIsAfterX() {
        return now.isAfter(Xdt);
    }

    boolean nowIsAfterY() {
        return now.isAfter(Ydt);
    }

    boolean nowIsAfterZ() {
        return now.isAfter(Zdt);
    }

    boolean nowIsAfterW() {
        return now.isAfter(Wdt);
    }

    public Stage nextStage(Stage stageNow) {
        if (!nowIsAfterX()) {
            return Stage.beforeX;
        }

        if (stageNow == Stage.beforeX) {
            return Stage.afterXbeforeY;
        }

        if (!nowIsAfterY()) {
            return Stage.afterXbeforeY;
        }

        if (stageNow == Stage.afterXbeforeY) {
            return Stage.afterYbeforeZ;
        }

        if (!nowIsAfterZ()) {
            return Stage.afterYbeforeZ;
        }

        if (stageNow == Stage.afterYbeforeZ) {
            return Stage.afterZbeforeW;
        }

        if (!nowIsAfterW()) {
            return Stage.afterZbeforeW;
        }

        if (stageNow == Stage.afterZbeforeW) {
            return Stage.released;
        }

        throw new IllegalStateException("trigger invalid state");
    }

}
