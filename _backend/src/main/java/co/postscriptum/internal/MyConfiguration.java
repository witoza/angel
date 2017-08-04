package co.postscriptum.internal;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Component
@Slf4j
public class MyConfiguration {

    private Properties properties;

    @Value("${my.secret_config_file}")
    private String configFileUrl;

    @PostConstruct
    public void init() throws IOException {

        if (StringUtils.isEmpty(configFileUrl)) {
            throw new IOException("You have to configure 'my.secret_config_file' property");
        }

        Path configFilePath = Paths.get(configFileUrl);

        log.info("Loading config from {}", configFilePath);

        try (Reader reader = new FileReader(configFilePath.toFile())) {
            properties = new Properties();
            properties.load(reader);
        }

    }

    public String getBitcoinPaymentSecret() {
        return properties.getProperty("bitcoin_payment_secret");
    }

    public String getBitcoinPaymentAdr() {
        return properties.getProperty("bitcoin_payment_addr");
    }

    public String getAdminEmail() {
        return properties.getProperty("admin_email");
    }

    public String getCaptchaSecret() {
        return properties.getProperty("captcha_secret");
    }

    public String getVerifiedUsersSalt() {
        return properties.getProperty("verified_users_salt");
    }

    public String getDbEncKey() {
        return properties.getProperty("db_enc_key");
    }

    public AwsConfig createAwsConfig() {
        return AwsConfig.builder()
                        .accessKeyId(properties.getProperty("aws.accessKeyId"))
                        .secretAccessKey(properties.getProperty("aws.secretAccessKey"))
                        .sesRegion(properties.getProperty("aws.ses.region"))
                        .s3Bucket(properties.getProperty("aws.s3.bucket"))
                        .sqsRegion(properties.getProperty("aws.sqs.region"))
                        .sqsQueueName(properties.getProperty("aws.sqs.queueName"))
                        .build();
    }

}
