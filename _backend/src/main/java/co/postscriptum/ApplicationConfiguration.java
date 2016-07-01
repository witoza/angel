package co.postscriptum;

import co.postscriptum.internal.AwsConfig;
import co.postscriptum.internal.MyConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityAutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

@Configuration
@EnableAspectJAutoProxy
@ComponentScan("co.postscriptum")
public class ApplicationConfiguration {

    @Bean
    public AwsConfig awsConfig(MyConfiguration myConfiguration) {
        return myConfiguration.createAwsConfig();
    }

    @Bean
    public RuntimeEnvironment env() {
        String activeProfile = System.getProperty("spring.profiles.active");
        if (activeProfile == null) {
            throw new IllegalArgumentException("you have to define spring.profiles.active");
        }
        return RuntimeEnvironment.valueOf(activeProfile.toUpperCase());
    }

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

}
