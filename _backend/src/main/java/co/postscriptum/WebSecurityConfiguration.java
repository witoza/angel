package co.postscriptum;

import co.postscriptum.security.MyAuthenticationEntryPoint;
import co.postscriptum.web.IncomingRequestFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Autowired
    private MyAuthenticationEntryPoint myAuthenticationEntryPoint;

    @Autowired
    private IncomingRequestFilter incomingRequestFilter;

    @Bean(name = "tokenRepository")
    public CookieCsrfTokenRepository tokenRepository() {
        CookieCsrfTokenRepository cookieCsrfTokenRepository = new CookieCsrfTokenRepository();
        cookieCsrfTokenRepository.setCookieHttpOnly(false);
        return cookieCsrfTokenRepository;
    }

    @Bean
    public FilterRegistrationBean registration(IncomingRequestFilter filter) {
        FilterRegistrationBean registration = new FilterRegistrationBean(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        http.authorizeRequests()
            .antMatchers("/api/file/**").hasAnyRole("USER")
            .antMatchers("/api/notif/**").hasAnyRole("USER", "ADMIN")
            .antMatchers("/api/msg/**").hasAnyRole("USER")
            .antMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")
            .antMatchers("/api/admin/**").hasAnyRole("ADMIN")
            .antMatchers("/api/payment/paid").permitAll()
            .antMatchers("/api/manage/info").permitAll()
            .antMatchers("/api/manage/shutdown").access("hasIpAddress('127.0.0.1') or hasIpAddress('localhost') or hasIpAddress('::1') or hasIpAddress('0:0:0:0:0:0:0:1')")
            .antMatchers("/api/manage/**").hasAnyRole("ADMIN")
            .antMatchers("/api/test/**").hasAnyRole("ADMIN")
            .and()
            .csrf().csrfTokenRepository(tokenRepository())
            .ignoringAntMatchers("/api/manage/shutdown")
            .and()
            .exceptionHandling().authenticationEntryPoint(myAuthenticationEntryPoint)
            .and()
            .addFilterAfter(incomingRequestFilter, FilterSecurityInterceptor.class);

    }

}
