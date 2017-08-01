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
        currentStage        | nextStage
        Stage.beforeX       | Stage.beforeX
        Stage.afterXbeforeY | Stage.beforeX
        Stage.afterYbeforeZ | Stage.beforeX
        Stage.afterZbeforeW | Stage.beforeX
    }

    def 'should move to proper state when userLastAccessTime is after X, before Y'() {
        given:
        TimeStages stages = timeStages(trigger, now.plusMinutes(trigger.x + 1), now)

        expect:
        stages.nextStage(currentStage) == nextStage

        where:
        currentStage        | nextStage
        Stage.beforeX       | Stage.afterXbeforeY
        Stage.afterXbeforeY | Stage.afterXbeforeY
        Stage.afterYbeforeZ | Stage.afterXbeforeY
        Stage.afterZbeforeW | Stage.afterXbeforeY
    }

    def 'should move to proper state when userLastAccessTime is after Y, before Z'() {
        given:
        TimeStages stages = timeStages(trigger, now.plusMinutes(trigger.x + trigger.y + 1), now)

        expect:
        stages.nextStage(currentStage) == nextStage

        where:
        currentStage        | nextStage
        Stage.beforeX       | Stage.afterXbeforeY
        Stage.afterXbeforeY | Stage.afterYbeforeZ
        Stage.afterYbeforeZ | Stage.afterYbeforeZ
        Stage.afterZbeforeW | Stage.afterYbeforeZ
    }

    def 'should move to proper state when userLastAccessTime is after Z, before W'() {
        given:
        TimeStages stages = timeStages(trigger, now.plusMinutes(trigger.x + trigger.y + trigger.z + 1), now)

        expect:
        stages.nextStage(currentStage) == nextStage

        where:
        currentStage        | nextStage
        Stage.beforeX       | Stage.afterXbeforeY
        Stage.afterXbeforeY | Stage.afterYbeforeZ
        Stage.afterYbeforeZ | Stage.afterZbeforeW
        Stage.afterZbeforeW | Stage.afterZbeforeW
    }

    def 'should move to proper state when userLastAccessTime is after W'() {
        given:
        TimeStages stages = timeStages(trigger, now.plusMinutes(trigger.x + trigger.y + trigger.z + trigger.w + 1), now)

        expect:
        stages.nextStage(currentStage) == nextStage

        where:
        currentStage        | nextStage
        Stage.beforeX       | Stage.afterXbeforeY
        Stage.afterXbeforeY | Stage.afterYbeforeZ
        Stage.afterYbeforeZ | Stage.afterZbeforeW
        Stage.afterZbeforeW | Stage.released
    }

    static timeStages(Trigger trigger, LocalDateTime now, LocalDateTime userLastAccessTime) {
        new TimeStages(trigger, now, timestamp(userLastAccessTime));
    }

    static timestamp(LocalDateTime dateTime) {
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

}
