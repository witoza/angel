package co.postscriptum.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.jsoup.Jsoup;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
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

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeHierarchyAdapter(byte[].class, new ByteArrayToBase64TypeAdapter())
            .create();

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
        return GSON.toJson(obj);
    }

    public static <T> T fromJson(Reader fr, TypeToken<T> tt) {
        return GSON.fromJson(fr, tt.getType());
    }

    public static <T> T fromJson(String data, TypeToken<T> tt) {
        return GSON.fromJson(data, tt.getType());
    }

    public static Map<String, Object> mapFromJson(String json) {
        return fromJson(new StringReader(json), new TypeToken<Map<String, Object>>() {
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

    private static class ByteArrayToBase64TypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {

        @Override
        public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws
                JsonParseException {
            return base64decode(json.getAsString());
        }

        @Override
        public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(base64encode(src));
        }
    }

}
