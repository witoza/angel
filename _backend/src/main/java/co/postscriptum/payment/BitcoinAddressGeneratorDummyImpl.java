package co.postscriptum.payment;

import co.postscriptum.model.bo.PaymentAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("dev")
@Slf4j
public class BitcoinAddressGeneratorDummyImpl implements BitcoinAddressGenerator {

    @Override
    public PaymentAddress generateNewAddress() {
        log.info("Generating dummy Payment Address");
        return PaymentAddress.builder()
                             .uuid(uuid())
                             .btcAddress("BTC-" + uuid())
                             .build();
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

}
