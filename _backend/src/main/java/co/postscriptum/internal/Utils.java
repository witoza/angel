package co.postscriptum.internal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.jsoup.Jsoup;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class Utils {

    private static final EmailValidator EMAIL_VALIDATOR = EmailValidator.getInstance();

    private static ObjectMapper getObjectMapper() {
        SimpleModule testModule = new SimpleModule();
        testModule.addSerializer(new ByteArrayToBase64Serializer());
        testModule.addDeserializer(byte[].class, new ByteArrayToBase64Deserializer());

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.registerModule(testModule);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    private static final ObjectMapper OBJECT_MAPPER = getObjectMapper();

    private static final Base64 BASE64 = new Base64();

    private static final Base32 BASE32 = new Base32();

    public static byte[] base32decode(String data) {
        return BASE32.decode(data);
    }

    public static String base32encode(byte[] data) {
        return BASE32.encodeToString(data);
    }

    public static byte[] base64decode(String data) {
        return BASE64.decode(data);
    }

    public static String base64encode(byte[] data) {
        return BASE64.encodeToString(data);
    }

    public static List<String> unique(List<String> input) {
        return new ArrayList<>(new LinkedHashSet<>(input));
    }

    public static String asString(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }

    public static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("mapper error", e);
        }
    }

    public static <T> T fromJson(Reader fr, TypeReference<T> tt) {
        try {
            return OBJECT_MAPPER.readValue(fr, tt);
        } catch (IOException e) {
            throw new IllegalStateException("mapper error", e);
        }
    }

    public static <T> T fromJson(String data, TypeReference<T> tt) {
        try {
            return OBJECT_MAPPER.readValue(data, tt);
        } catch (IOException e) {
            throw new IllegalStateException("mapper error", e);
        }
    }

    public static Map<String, Object> mapFromJson(String json) {
        return fromJson(new StringReader(json), new TypeReference<Map<String, Object>>() {
        });
    }

    public static String randKey(String prefix) {
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        if (StringUtils.isEmpty(prefix)) {
            return uuid;
        }
        return prefix + "_" + uuid;
    }

    public static boolean isValidEmail(String email) {
        return EMAIL_VALIDATOR.isValid(email);
    }

    public static List<String> extractValidEmails(String... args) {

        return Arrays.stream(StringUtils.join(args, ";").split(";|,"))
                     .map(String::trim)
                     .filter(Utils::isValidEmail)
                     .distinct()
                     .collect(Collectors.toList());
    }

    public static <T> Optional<T> getLast(List<T> list) {
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(list.size() - 1));
    }

    public static ZonedDateTime fromTimestamp(long ts) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault());
    }

    public static String format(long ts) {
        return format(fromTimestamp(ts));
    }

    public static String format(ZonedDateTime dt) {
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss zzzZZZ"));
    }

    public static <T> ArrayList<T> asArrayList(T... args) {
        return new ArrayList<>(Arrays.asList(args));
    }

    public static String urlEncode(String data) {
        try {
            return URLEncoder.encode(data, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("unsupported utf_8 ?", e);
        }
    }

    public static void limit(List<?> list, int limit) {
        while (list.size() > limit) {
            list.remove(0);
        }
    }

    public static void toSafeString(StringBuilder sb, Map<String, Object> params) {
        sb.append("{");
        String comma = "";
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            String keyLc = key.toLowerCase();
            Object value = entry.getValue();

            sb.append(comma).append(key).append("=");
            if (keyLc.contains("key") || keyLc.contains("token") || keyLc.contains("passwd") || keyLc.contains("password")
                    || keyLc.equals("content")) {
                value = "[secret]";
            }
            if (value instanceof Map) {
                toSafeString(sb, (Map<String, Object>) value);
            } else {
                sb.append(value);
            }
            comma = ", ";
        }
        sb.append("}");

    }

    public static String exceptionInfo(Throwable e) {
        StringBuilder sb = new StringBuilder();
        while (e != null) {
            sb.append(MessageFormat.format("{0}: {1}", e.getClass().getSimpleName(), e.getMessage()));
            if (e.getCause() != null) {
                sb.append(", cause:\n");
            }
            e = e.getCause();
        }
        return sb.toString();
    }

    public static String getRemoteIp(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    public static Optional<String> getCookieValue(HttpServletRequest request, String cookieName) {
        return Arrays.stream(request.getCookies())
                     .filter(cookie -> StringUtils.equals(cookieName, cookie.getName()))
                     .map(Cookie::getValue)
                     .findAny();
    }

    public static long minutesInMs(long minutes) {
        return minutes * 60 * 1000;
    }

    public static long daysInMs(long days) {
        return minutesInMs(days * 24 * 60);
    }

    public static String asSafeText(String data) {
        return Jsoup.parse(data).text();
    }


    private static class ByteArrayToBase64Serializer extends JsonSerializer<byte[]> {

        @Override
        public void serialize(byte[] value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(base64encode(value));
        }

        @Override
        public Class<byte[]> handledType() {
            return byte[].class;
        }

    }

    private static class ByteArrayToBase64Deserializer extends JsonDeserializer<byte[]> {

        @Override
        public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return base64decode(p.getCodec().readTree(p).toString());
        }

        @Override
        public Class<?> handledType() {
            return byte[].class;
        }

    }

}
