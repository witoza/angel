package co.postscriptum.internal;

import co.postscriptum.model.bo.Lang;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@AllArgsConstructor
@Slf4j
public class I18N {

    private static final Pattern I18N_KEY_REGEX_PATTERN = Pattern.compile("(%.+?%)");

    private final MustacheFactory mf = new DefaultMustacheFactory();

    private final MessageSource messageSource;

    public String translate(Lang lang, String key) {
        return this.translate(lang, key, new java.util.HashMap<>());
    }

    public String translate(Lang lang, String key, Map<String, Object> context) {

        String expanded = expand(new Locale(lang.toString()), key);

        Mustache mustache = mf.compile(new StringReader(expanded), "tmp");

        StringWriter sw = new StringWriter();
        mustache.execute(sw, context);
        sw.flush();

        return sw.toString();
    }

    private String expand(Locale locale, String text) {
        Matcher m = I18N_KEY_REGEX_PATTERN.matcher(text);
        while (m.find()) {

            final String key = m.group(1);
            final String stripped = StringUtils.strip(key, "%");

            String value;

            try {
                value = messageSource.getMessage(stripped, null, locale);
            } catch (NoSuchMessageException e) {
                log.warn("missing translation for key: " + key + ", lang:" + locale + ". Using EN as fallback.");
                value = messageSource.getMessage(stripped, null, new Locale(Lang.en.toString()));
            }

            // the value now can include other %keys%, need to expand them as well
            final String expandedValue = expand(locale, value);

            text = text.replaceAll(key, expandedValue);
        }
        return text;
    }

}
