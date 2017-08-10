package co.postscriptum.service

import co.postscriptum.model.bo.Lang
import org.springframework.context.support.ResourceBundleMessageSource
import spock.lang.Specification

class I18NSpec extends Specification {

    I18N i18n

    def setup() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource()
        messageSource.setBasename('messages')

        i18n = new I18N(messageSource)
    }

    def 'should translate in PL an EN'() {
        Map<String, Object> context = new HashMap<>()
        context.put('key_uri', 'EMAILTO')

        expect:
        i18n.translate(Lang.pl, '%totp_details%', context).contains('EMAILTO</pre>')
        i18n.translate(Lang.pl, '%totp_details%', context).contains('Detale klucza OTP')

        i18n.translate(Lang.en, '%totp_details%', context).contains('EMAILTO</pre>')
        i18n.translate(Lang.en, '%totp_details%', context).contains('OTP Key details')
    }

}
