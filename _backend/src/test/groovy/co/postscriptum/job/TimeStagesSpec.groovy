package co.postscriptum.job

import co.postscriptum.model.bo.Trigger
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.time.temporal.ChronoUnit

import static co.postscriptum.model.bo.Trigger.Stage

class TimeStagesSpec extends Specification {

    def trigger = Trigger.builder()
                         .enabled(true)
                         .timeUnit(ChronoUnit.MINUTES)
                         .x(5)
                         .y(10)
                         .z(15)
                         .w(20)
                         .stage(null)
                         .build()

    def now = LocalDateTime.of(1985, Month.NOVEMBER, 17, 06, 00, 01)

    def 'should move to proper state when userLastAccessTime is before X'() {
        given:
        TimeStages stages = timeStages(trigger, now.plusMinutes(4), now)

        expect:
        stages.nextStage(currentStage) == nextStage

        where:
        currentStage                 | nextStage
        Stage.BEFORE_X               | Stage.BEFORE_X
        Stage.AFTER_X_BEFORE_Y       | Stage.BEFORE_X
        Stage.AFTER_Y_BEFORE_Z       | Stage.BEFORE_X
        Stage.AFTER_Z_BEFORE_RELEASE | Stage.BEFORE_X
    }

    def 'should move to proper state when userLastAccessTime is after X, before Y'() {
        given:
        TimeStages stages = timeStages(trigger, now.plusMinutes(trigger.x + 1), now)

        expect:
        stages.nextStage(currentStage) == nextStage

        where:
        currentStage                 | nextStage
        Stage.BEFORE_X               | Stage.AFTER_X_BEFORE_Y
        Stage.AFTER_X_BEFORE_Y       | Stage.AFTER_X_BEFORE_Y
        Stage.AFTER_Y_BEFORE_Z       | Stage.AFTER_X_BEFORE_Y
        Stage.AFTER_Z_BEFORE_RELEASE | Stage.AFTER_X_BEFORE_Y
    }

    def 'should move to proper state when userLastAccessTime is after Y, before Z'() {
        given:
        TimeStages stages = timeStages(trigger, now.plusMinutes(trigger.x + trigger.y + 1), now)

        expect:
        stages.nextStage(currentStage) == nextStage

        where:
        currentStage                 | nextStage
        Stage.BEFORE_X               | Stage.AFTER_X_BEFORE_Y
        Stage.AFTER_X_BEFORE_Y       | Stage.AFTER_Y_BEFORE_Z
        Stage.AFTER_Y_BEFORE_Z       | Stage.AFTER_Y_BEFORE_Z
        Stage.AFTER_Z_BEFORE_RELEASE | Stage.AFTER_Y_BEFORE_Z
    }

    def 'should move to proper state when userLastAccessTime is after Z, before W'() {
        given:
        TimeStages stages = timeStages(trigger, now.plusMinutes(trigger.x + trigger.y + trigger.z + 1), now)

        expect:
        stages.nextStage(currentStage) == nextStage

        where:
        currentStage                 | nextStage
        Stage.BEFORE_X               | Stage.AFTER_X_BEFORE_Y
        Stage.AFTER_X_BEFORE_Y       | Stage.AFTER_Y_BEFORE_Z
        Stage.AFTER_Y_BEFORE_Z       | Stage.AFTER_Z_BEFORE_RELEASE
        Stage.AFTER_Z_BEFORE_RELEASE | Stage.AFTER_Z_BEFORE_RELEASE
    }

    def 'should move to proper state when userLastAccessTime is after W'() {
        given:
        TimeStages stages = timeStages(trigger, now.plusMinutes(trigger.x + trigger.y + trigger.z + trigger.w + 1), now)

        expect:
        stages.nextStage(currentStage) == nextStage

        where:
        currentStage                 | nextStage
        Stage.BEFORE_X               | Stage.AFTER_X_BEFORE_Y
        Stage.AFTER_X_BEFORE_Y       | Stage.AFTER_Y_BEFORE_Z
        Stage.AFTER_Y_BEFORE_Z       | Stage.AFTER_Z_BEFORE_RELEASE
        Stage.AFTER_Z_BEFORE_RELEASE | Stage.RELEASED
    }

    static timeStages(Trigger trigger, LocalDateTime now, LocalDateTime userLastAccessTime) {
        new TimeStages(trigger, now, timestamp(userLastAccessTime));
    }

    static timestamp(LocalDateTime dateTime) {
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

}
