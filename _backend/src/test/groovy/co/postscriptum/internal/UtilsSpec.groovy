package co.postscriptum.internal

import co.postscriptum.exception.BadRequestException
import spock.lang.Specification

class UtilsSpec extends Specification {

    def 'should base64encode'() {
        expect:
        Utils.base64encode('testabc'.getBytes()) == 'dGVzdGFiYw=='
        Utils.base64decode('dGVzdGFiYw==') == 'testabc'.getBytes()
    }

    def 'should unique'() {
        expect:
        Utils.unique(['1', '1', '1', '2', '1', '1', '3', '3', '3', '2', '1']) == ['1', '2', '3']
    }

    def 'should extract valid emails'() {
        expect:
        Utils.extractValidEmails("") == []
        Utils.extractValidEmails("a@a.pl, b@a.pl, bad, a@bad_email.pl ;   c@c.pl  ;d@d.pl") == ['a@a.pl', 'b@a.pl', 'c@c.pl', 'd@d.pl']
    }

    def 'should string dangerous html'() {
        expect:
        Utils.asSafeText("<html>hello</html>") == "hello"
    }

    def 'should print exception'() {
        expect:
        Utils.basicExceptionInfo(new BadRequestException("hey", new RuntimeException("very bad", new NullPointerException()))) ==
                """BadRequestException: hey, cause:
RuntimeException: very bad, cause:
NullPointerException: null"""
    }

}
