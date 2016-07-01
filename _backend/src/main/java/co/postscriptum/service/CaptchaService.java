package co.postscriptum.service;

import co.postscriptum.internal.MyConfiguration;
import co.postscriptum.internal.Utils;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Map;

@Component
@Slf4j
public class CaptchaService {

    private static final String CAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    @Autowired
    private MyConfiguration configuration;

    public boolean verify(String recaptchaResponse) {
        log.info("Verifying captcha2 {}", recaptchaResponse);

        if (StringUtils.isEmpty(recaptchaResponse)) {
            return false;
        }

        try {
            URL obj = new URL(CAPTCHA_VERIFY_URL);
            HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
            con.setRequestMethod("POST");

            con.setDoOutput(true);

            try (BufferedOutputStream wr = new BufferedOutputStream(con.getOutputStream())) {
                wr.write(("secret=" + configuration.getCaptchaSecret() + "&response=" + recaptchaResponse).getBytes());
            }

            final int responseCode = con.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("http response code is " + responseCode);
            }

            try (Reader fr = new InputStreamReader(con.getInputStream())) {

                Map<String, Object> result = Utils.fromJson(fr, new TypeToken<Map<String, Object>>() {
                });

                return Boolean.parseBoolean(result.get("success").toString());

            }

        } catch (IOException e) {
            log.error("captcha validation error", e);
            return false;
        }

    }

}
