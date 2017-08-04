package co.postscriptum.internal

import spock.lang.Specification

class QRGeneratorSpec extends Specification {

    def 'should generate qr'() {
        expect:
        QRGenerator.createQr("http://www.google.com").size() > 500
    }

}
