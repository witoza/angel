package co.postscriptum.internal;

import co.postscriptum.exception.BadRequestException;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class Params {

    private final Map<String, Object> params;

    private Params(Map<String, Object> params) {
        this.params = ImmutableMap.copyOf(params);
    }

    public static Params of(Map<String, Object> params) {
        return new Params(params);
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String name) {
        return (T) params.get(name);
    }

    public <T> T require(String name) {
        T value = get(name);
        if (value == null) {
            throw new BadRequestException(String.format("missing required param '%s'", name));
        }
        return value;
    }

}
