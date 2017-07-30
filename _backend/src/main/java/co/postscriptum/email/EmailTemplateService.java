package co.postscriptum.email;

import com.amazonaws.util.IOUtils;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class EmailTemplateService {

    private final MustacheFactory mf = new DefaultMustacheFactory();

    private Mustache emailTemplateTemplate;

    @PostConstruct
    public void init() {
        emailTemplateTemplate = mf.compile(new StringReader(getEmailTemplate()), "emailTemplateTemplate");
    }

    private String getEmailTemplate() {
        try {
            return IOUtils.toString(new ClassPathResource("emailTemplate.html").getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("can't obtain email template");
        }
    }

    public String getFormattedContent(Envelope envelope) {
        return getFormattedContent(envelope.getMsgHeader(), envelope.getMsgContent(), envelope.getMsgFooter());
    }

    public String getFormattedContent(String msgHeader, String msgContent, String msgFooter) {

        Map<String, Object> context = new HashMap<>();
        context.put("msgHeader", msgHeader);
        context.put("msgContent", msgContent);
        context.put("msgFooter", msgFooter);

        StringWriter sw = new StringWriter();
        emailTemplateTemplate.execute(sw, context);
        sw.flush();

        return sw.toString();
    }

}
