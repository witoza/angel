package co.postscriptum.payment

import co.postscriptum.db.Account
import co.postscriptum.db.DB
import co.postscriptum.model.bo.DataFactory
import co.postscriptum.model.bo.PaymentAddress
import co.postscriptum.model.bo.Trigger
import co.postscriptum.model.bo.User
import co.postscriptum.model.bo.UserData
import co.postscriptum.model.bo.UserInternal
import co.postscriptum.model.bo.UserPlan
import spock.lang.Specification

import java.util.function.Consumer
import java.util.function.Predicate

class BitcoinServiceSpec extends Specification {

    BitcoinAddressGenerator bitcoinAddressGenerator = Mock(BitcoinAddressGenerator)

    DB db = Mock(DB)

    BitcoinService bitcoinService = new BitcoinService(bitcoinAddressGenerator, db)

    def 'should receive payment'() {
        given:
        User user = User.builder()
                        .uuid(uuid())
                        .active(true)
                        .trigger(Trigger.builder()
                                        .stage(Trigger.Stage.RELEASED)
                                        .build())
                        .paymentAddress(PaymentAddress.builder()
                                                      .uuid(uuid())
                                                      .btcAddress("BTC-" + uuid())
                                                      .build()
        )
                        .build()

        UserInternal internal = new UserInternal()
        internal.setUserPlan(new UserPlan())

        UserData userData = DataFactory.newUserData(user, internal)

        PaymentAddress payment = userData.getUser().getPaymentAddress()

        when:
        db.getAccount(_ as Predicate<Account>) >> { arguments ->
            [account(userData)].stream()
                               .filter(arguments[0] as Predicate<Account>)
                               .findFirst()
        }
        db.withLoadedAccount(_ as Account, _ as Consumer<Account>) >> { arguments ->
            (arguments[1] as Consumer<Account>).accept(arguments[0] as Account)
        }
        bitcoinService.paymentReceived(payment.getUuid(), 'HASH', '3.14')

        then:
        user.getPaymentAddress() == null
        userData.internal.userPlan.payments[0].amount == '3.14 Satoshi'
        userData.internal.userPlan.payments[0].details == "Payment of 3.14 Satoshi has been sent to BTC address ${payment.getBtcAddress()} in transaction HASH"
    }

    def 'should assign payment address'() {
        given:
        User user = User.builder()
                        .uuid(uuid())
                        .active(true)
                        .trigger(Trigger.builder()
                                        .stage(Trigger.Stage.RELEASED)
                                        .build())
                        .paymentAddress(PaymentAddress.builder()
                                                      .uuid(uuid())
                                                      .btcAddress('btcAddress')
                                                      .build())
                        .build()

        UserInternal internal = new UserInternal()
        internal.setUserPlan(new UserPlan())

        UserData userData = DataFactory.newUserData(user, internal)

        and:
        db.getUserUnloadedAccounts() >> (1..5).collect { account() }.stream()

        when:
        def payment = bitcoinService.getPaymentForUser(userData)

        then:
        payment.getBtcAddress() == 'btcAddress'
        payment.getAssignedTime() > 0

        when:
        user.setPaymentAddress(null)
        payment = bitcoinService.getPaymentForUser(userData)

        then:
        payment.getBtcAddress() != 'btcAddress'
        1 * bitcoinAddressGenerator.generateNewAddress() >> {
            new BitcoinAddressGeneratorDummyImpl().generateNewAddress()
        }
        user.getPaymentAddress() == payment
    }

    def account(UserData userData) {
        Account account = Mock(Account)
        if (userData == null) {
            userData = DataFactory.newUserData(DataFactory.newUser(), null)
        }
        account.getUserData() >> userData
        account
    }

    def uuid() {
        UUID.randomUUID().toString()
    }

}
