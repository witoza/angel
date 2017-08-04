package co.postscriptum.service

import co.postscriptum.stk.ShortTimeKey
import co.postscriptum.stk.ShortTimeKeyService
import spock.lang.Specification

class ShortTimeKeyServiceSpec extends Specification {

    ShortTimeKeyService service = new ShortTimeKeyService()

    def 'should create & get key'() {
        when:
        ShortTimeKey stk = service.create("user@name", ShortTimeKey.Type.LOGIN_FROM_NOT_VERIFIED_BROWSER_TOKEN)

        then:
        service.getByKey(stk.getKey(), ShortTimeKey.Type.LOGIN_FROM_NOT_VERIFIED_BROWSER_TOKEN).isPresent()

        when:
        service.removeKey(stk)

        then:
        !service.getByKey(stk.getKey(), ShortTimeKey.Type.LOGIN_FROM_NOT_VERIFIED_BROWSER_TOKEN).isPresent()
    }

}
