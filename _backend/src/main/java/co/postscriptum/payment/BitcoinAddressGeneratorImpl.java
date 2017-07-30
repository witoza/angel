package co.postscriptum.payment;

import co.postscriptum.exception.InternalException;
import co.postscriptum.internal.MyConfiguration;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.PaymentAddress;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Map;
import java.util.UUID;

@Component
@Profile(value = {"prod", "cert"})
@Slf4j
public class BitcoinAddressGeneratorImpl implements BitcoinAddressGenerator {

    @Value("${my.host.url}")
    private String hostUrl;

    @Autowired
    private MyConfiguration configuration;

    private String createCallbackUrl(String paymentAddressUuid) {
        return hostUrl + "/api/payment/paid?uuid=" + paymentAddressUuid + "&secret=" +
                configuration.getBitcoinPaymentSecret();
    }

    public PaymentAddress generateNewAddress() {

        log.info("generate new bitcoin address");

        PaymentAddress paymentAddress = new PaymentAddress();
        paymentAddress.setUuid(UUID.randomUUID().toString());

        String url = configuration.getBitcoinPaymentAdr();
        url = url.replace("$callback_url", Utils.urlEncode(createCallbackUrl(paymentAddress.getUuid())));


        try {
            URL obj = new URL(url);
            HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setDoOutput(true);

            try (Reader fr = new InputStreamReader(con.getInputStream())) {

                Map<String, Object> result = Utils.fromJson(fr, new TypeToken<Map<String, Object>>() {
                });
                paymentAddress.setBtcAddress((String) result.get("address"));
            }

            return paymentAddress;
        } catch (IOException e) {
            throw new InternalException("exception occurred while connecting to Bitcoin payment service", e);
        }
    }
}
