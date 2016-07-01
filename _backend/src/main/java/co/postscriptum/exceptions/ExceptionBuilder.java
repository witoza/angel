package co.postscriptum.exceptions;

import java.util.function.Supplier;

public class ExceptionBuilder {

    public static Supplier<BadRequestException> missingClass(Class<?> clazz, String msg) {
        return () -> new BadRequestException("can't find " + clazz.getSimpleName() + " with " + msg);
    }

    public static Supplier<BadRequestException> badRequest(String msg) {
        return () -> new BadRequestException(msg);
    }

}
