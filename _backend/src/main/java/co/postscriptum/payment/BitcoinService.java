package co.postscriptum.payment;

import co.postscriptum.db.Account;
import co.postscriptum.db.DB;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.PaymentAddress;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.bo.UserPlan;
import co.postscriptum.service.UserDataHelper;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class BitcoinService {

    @Autowired
    private BitcoinAddressGenerator bitcoinAddressGenerator;

    @Autowired
    private DB db;

    @Synchronized
    public boolean paymentReceived(String paymentUuid, String transactionHash, String value) {
        log.info("payment with: uuid={}, transactionHash={}, value={} received", paymentUuid, transactionHash, value);

        Optional<Account> accountWithAddress = db.getAccountWithAssignedPayment(paymentUuid);

        if (!accountWithAddress.isPresent()) {
            log.error("can't find Account with paymentAddress={}", paymentUuid);
            return false;
        }

        log.info("Account with that payment uuid has been found");

        db.withLoadedAccount(accountWithAddress, account -> {
            UserData userData = account.getUserData();

            String msg = "Payment of " + value + " Satoshi has been sent to BTC address " +
                    userData.getUser().getPaymentAddress().getBtcAddress() + " in transaction " + transactionHash;

            userData.getInternal().getUserPlan().getPayments().add(UserPlan.Payment.builder()
                                                                                   .time(System.currentTimeMillis())
                                                                                   .amount(value + " Satoshi")
                                                                                   .details(msg)
                                                                                   .build());

            userData.getInternal().getUserPlan().setPaidUntil(System.currentTimeMillis() + Utils.daysInMs(356));
            userData.getUser().setPaymentAddress(null);

            new UserDataHelper(userData).addNotification(msg + ".\nYour account has been extended for another year.");

        });

        return true;
    }

    public PaymentAddress getPaymentForUser(UserData userData) {
        log.info("get payment address for user {}", userData.getUser().getUuid());

        PaymentAddress paymentAddress = getPaymentForUserImpl(userData.getUser());
        paymentAddress.setAssignedTime(System.currentTimeMillis());

        userData.getUser().setPaymentAddress(paymentAddress);

        return paymentAddress;
    }

    private PaymentAddress getPaymentForUserImpl(User user) {

        // try to use address which was previously assigned for that user
        if (user.getPaymentAddress() != null) {
            log.info("reusing assigned address");
            return user.getPaymentAddress();
        }

        //try to get unused address from the pool (>30min)
        Optional<PaymentAddress> unused = db.harvestPaymentAddressFromUnloadedAccounts();
        if (unused.isPresent()) {
            log.info("using other user unused address");
            return unused.get();
        }

        //get fresh one
        log.info("generating new one");
        return bitcoinAddressGenerator.generateNewAddress();
    }

}
