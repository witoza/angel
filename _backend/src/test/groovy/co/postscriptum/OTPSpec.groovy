package co.postscriptum

import org.apache.commons.codec.binary.Hex
import org.jboss.security.otp.TimeBasedOTP
import spock.lang.Specification

class OTPSpec extends Specification {

    def "should generated token be a number"() {
        given:
        String secret = Hex.encodeHexString("secret".getBytes())

        when:
        String token = TimeBasedOTP.generateTOTP(secret, 6)

        then:
        Integer.parseInt(token) > 1
    }

}
