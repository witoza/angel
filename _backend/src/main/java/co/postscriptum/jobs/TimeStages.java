package co.postscriptum.jobs;

import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.Trigger;
import co.postscriptum.model.bo.Trigger.Stage;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Getter
public class TimeStages {

    private final ZonedDateTime lastAccess;
    private final ZonedDateTime Xdt;
    private final ZonedDateTime Ydt;
    private final ZonedDateTime Zdt;
    private final ZonedDateTime Wdt;
    private final ZonedDateTime now;

    public TimeStages(Trigger trigger, long userLastAccess) {
        ChronoUnit timeUnit = trigger.getTimeUnit();

        lastAccess = Utils.fromTimestamp(userLastAccess);
        Xdt = lastAccess.plus(trigger.getX(), timeUnit);
        Ydt = Xdt.plus(trigger.getY(), timeUnit);
        Zdt = Ydt.plus(trigger.getZ(), timeUnit);
        Wdt = Zdt.plus(trigger.getW(), timeUnit);
        now = ZonedDateTime.now();
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

    public boolean nowIsAfterX() {
        return now.isAfter(Xdt);
    }

    public boolean nowIsAfterY() {
        return now.isAfter(Ydt);
    }

    public boolean nowIsAfterZ() {
        return now.isAfter(Zdt);
    }

    public boolean nowIsAfterW() {
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