package co.postscriptum

import co.postscriptum.internal.I18N
import co.postscriptum.model.bo.Lang
import org.springframework.context.support.ResourceBundleMessageSource
import spock.lang.Specification

class I18NTest extends Specification {

    I18N i18n

    def setup() {

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource()
        messageSource.setBasename("messages")

        i18n = new I18N(messageSource)
    }

    def "should translate in PL"() {
        given:
        Lang lang = Lang.pl

        Map<String, Object> context = new HashMap<>()
        context.put("key_uri", "EMAILTO")

        expect:
        i18n.translate(lang, "%totp_details%", context).contains("EMAILTO</pre>")
        i18n.translate(lang, "%totp_details%", context).contains("Detale klucza OTP")
    }

    def "should translate in EN"() {
        given:
        Lang lang = Lang.en

        Map<String, Object> context = new HashMap<>()
        context.put("key_uri", "EMAILTO")

        expect:
        i18n.translate(lang, "%totp_details%", context).contains("EMAILTO</pre>")
        i18n.translate(lang, "%totp_details%", context).contains("OTP Key details")
    }

}
