package co.postscriptum.web;

import co.postscriptum.db.Account;
import co.postscriptum.db.DB;
import co.postscriptum.model.bo.UserData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Slf4j
@Component
public class UserDataArgumentResolver implements HandlerMethodArgumentResolver {

    @Autowired
    private DB db;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return UserData.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {

        log.info("Resolving logged UserData");

        Account account = db.requireAccountByUsername(AuthenticationHelper.requireLoggedUsername());
        account.assertLockIsHeldByCurrentThread();
        db.loadAccount(account);

        return account.getUserData();
    }

}
