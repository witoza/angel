package co.postscriptum

import co.postscriptum.internal.Utils
import spock.lang.Specification

class UtilsTest extends Specification {

    def "should base64encode"() {

        expect:
        Utils.base64encode("testabc".getBytes()) == "dGVzdGFiYw=="
        Utils.base64decode("dGVzdGFiYw==") == "testabc".getBytes()

    }

    def "should unique"() {

        expect:
        Utils.unique(["1", "1", "1", "2", "1", "1", "3", "3", "3", "2", "1"]) == ["1", "2", "3"]

    }

}
