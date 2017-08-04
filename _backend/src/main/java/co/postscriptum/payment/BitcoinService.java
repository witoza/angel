package co.postscriptum.payment;

import co.postscriptum.db.Account;
import co.postscriptum.db.DB;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.PaymentAddress;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;
import co.postscriptum.model.bo.UserPlan;
import co.postscriptum.service.UserDataHelper;
import lombok.AllArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Optional;

@Component
@Slf4j
@AllArgsConstructor
public class BitcoinService {

    private final BitcoinAddressGenerator bitcoinAddressGenerator;

    private final DB db;

    @Synchronized
    public Optional<Account> paymentReceived(String paymentUuid, String transactionHash, String value) {
        log.info("Payment with: uuid: {}, transactionHash: {}, value: {} has been received", paymentUuid, transactionHash, value);

        Optional<Account> accountWithAddressOpt = getAccountWithAssignedPayment(paymentUuid);

        if (!accountWithAddressOpt.isPresent()) {
            log.error("Can't find Account with paymentAddress: {}", paymentUuid);
            return Optional.empty();
        }

        log.info("User with payment.uuid: {} is user.uuid: {}", paymentUuid,
                 accountWithAddressOpt.get().getUserData().getUser().getUuid());

        db.withLoadedAccount(accountWithAddressOpt.get(), account -> {
            UserData userData = account.getUserData();

            String msg = "Payment of " + value + " Satoshi has been sent to BTC address " +
                    userData.getUser().getPaymentAddress().getBtcAddress() + " in transaction " + transactionHash;

            UserPlan userPlan = userData.getInternal().getUserPlan();
            if (userPlan.getPayments() == null) {
                userPlan.setPayments(new ArrayList<>());
            }
            userPlan.getPayments().add(UserPlan.Payment.builder()
                                                       .time(System.currentTimeMillis())
                                                       .amount(value + " Satoshi")
                                                       .details(msg)
                                                       .build());

            userPlan.setPaidUntil(System.currentTimeMillis() + Utils.daysInMs(356));
            userData.getUser().setPaymentAddress(null);

            new UserDataHelper(userData).addNotification(msg + ".\nYour account has been extended for another year.");

        });

        return accountWithAddressOpt;
    }

    private Optional<Account> getAccountWithAssignedPayment(String paymentUuid) {
        return db.getAccount(account -> {
            PaymentAddress paymentAddress = account.getUserData().getUser().getPaymentAddress();
            if (paymentAddress == null) {
                return false;
            }
            return paymentAddress.getUuid().equals(paymentUuid);
        });
    }

    public PaymentAddress getPaymentForUser(UserData userData) {
        log.info("Getting payment address for user.uuid: {}", userData.getUser().getUuid());

        PaymentAddress paymentAddress = getPaymentForUserImpl(userData.getUser());
        paymentAddress.setAssignedTime(System.currentTimeMillis());

        userData.getUser().setPaymentAddress(paymentAddress);

        return paymentAddress;
    }

    private PaymentAddress getPaymentForUserImpl(User user) {

        // try to use address which was previously assigned for that user
        if (user.getPaymentAddress() != null) {
            log.info("Reusing same user assigned address");
            return user.getPaymentAddress();
        }

        return harvestPaymentAddressFromUnloadedAccounts()
                .map(paymentAddress -> {
                    log.info("Using other user unused payment address");
                    return paymentAddress;
                })
                .orElseGet(() -> {
                    log.info("Can't reuse address, generating new one");
                    return bitcoinAddressGenerator.generateNewAddress();
                });

    }

    private Optional<PaymentAddress> harvestPaymentAddressFromUnloadedAccounts() {
        return db.getUserUnloadedAccounts()
                 .filter(a -> isPaymentAddressUnused(a.getUserData().getUser().getPaymentAddress()))
                 .findAny()
                 .map(account -> {
                     try {
                         account.lock();
                         User user = account.getUserData().getUser();
                         PaymentAddress paymentAddress = user.getPaymentAddress();
                         user.setPaymentAddress(null);
                         return paymentAddress;
                     } finally {
                         account.unlock();
                     }
                 });
    }

    private boolean isPaymentAddressUnused(PaymentAddress paymentAddress) {
        // after 30 minutes, unloaded user's paymentAddress can be reused
        return paymentAddress != null &&
                paymentAddress.getAssignedTime() + Utils.minutesToMillis(30) < System.currentTimeMillis();
    }

}
