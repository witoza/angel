package co.postscriptum.controller;

import co.postscriptum.exception.BadRequestException;
import co.postscriptum.internal.MyConfiguration;
import co.postscriptum.payment.BitcoinService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment")
@Slf4j
@AllArgsConstructor
public class PaymentController {

    private final BitcoinService bitcoinService;

    private final MyConfiguration configuration;

    @GetMapping("/paid")
    @ResponseBody
    public String paid(@RequestParam("uuid") String uuid,
                       @RequestParam("value") String value,
                       @RequestParam("transaction_hash") String transaction_hash,
                       @RequestParam("secret") String secret) {

        if (!secret.equalsIgnoreCase(configuration.getBitcoinPaymentSecret())) {
            throw new BadRequestException("Invalid payment secret");
        }

        bitcoinService.paymentReceived(uuid, transaction_hash, value);

        return "*ok*";
    }

}
