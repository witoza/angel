package co.postscriptum.payment;

import co.postscriptum.model.bo.PaymentAddress;

public interface BitcoinAddressGenerator {

    PaymentAddress generateNewAddress();

}
