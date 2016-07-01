package co.postscriptum.service;

import co.postscriptum.email.Envelope;
import co.postscriptum.email.EnvelopeType;
import co.postscriptum.internal.I18N;
import co.postscriptum.internal.Utils;
import co.postscriptum.model.bo.Lang;
import co.postscriptum.model.bo.User;
import co.postscriptum.model.bo.UserData;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class EnvelopeCreatorService {

    @Autowired
    private I18N i18n;

    @Value("${my.host.url}")
    private String hostUrl;

    @Value("${server.servlet-path}")
    private String dispatcherPrefix;


    public Map<String, Object> createContext(UserData userData) {

        Map<String, Object> context = new HashMap<>();
        context.put("uuid", Utils.randKey("EMAIL"));
        context.put("host", hostUrl);
        context.put("host_backend", StringUtils.removeEndIgnoreCase(hostUrl + dispatcherPrefix, "/"));

        if (userData != null) {
            User user = userData.getUser();
            context.put("user", user);
            context.put("userData", userData);
            context.put("encoded_username", Utils.urlEncode(user.getUsername()));
            context.put("encoded_user_uuid", Utils.urlEncode(user.getUuid()));
        }

        return context;

    }

    public Envelope create(EnvelopeType type,
                           UserData userData,
                           String recipient,
                           String templateKey,
                           Map<String, Object> contextIn) {

        Map<String, Object> context = createContext(userData);
        context.putAll(contextIn);
        context.put("email_to", recipient);

        Lang lang = ObjectUtils.firstNonNull((Lang) context.get("lang"), userData.getInternal().getLang());

        String msgHeader = i18n.translate(lang, "%email_header%", context);
        String msgContent = i18n.translate(lang, "%" + templateKey + ".content%", context);
        String msgContentFooter = i18n.translate(lang, "%email_content_footer%", context);
        String msgFooter = i18n.translate(lang, "%email_footer%", context);
        String title = i18n.translate(lang, "%" + templateKey + ".title%", context);

        Map<String, String> headers = ImmutableMap.<String, String>builder()
                .put("userUuid", userData.getUser().getUuid())
                .put("title", title)
                .put("recipient", recipient)
                .build();

        return Envelope.builder()
                       .type(type)
                       .headers(headers)
                       .recipient(recipient)
                       .title(title)
                       .msgHeader(msgHeader)
                       .msgContent(msgContent + msgContentFooter)
                       .msgFooter(msgFooter)
                       .build();
    }


}
