package co.postscriptum.exception;

import java.util.function.Supplier;

public class ExceptionBuilder {

    public static Supplier<BadRequestException> missingObject(Class<?> clazz, String msg) {
        return () -> new BadRequestException(String.format("Can't find %s with %s", clazz.getSimpleName(), msg));
    }

    public static Supplier<BadRequestException> badRequest(String msg) {
        return () -> new BadRequestException(msg);
    }

}
