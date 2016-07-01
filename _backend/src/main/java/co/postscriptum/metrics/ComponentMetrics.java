package co.postscriptum.metrics;

import lombok.Synchronized;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
@Aspect
public class ComponentMetrics {

    private final Map<String, Map<String, MethodUsageInfo>> usage = new TreeMap<>();

    @Synchronized
    public void put(Class<?> clazz, String method, long elapsedTime, Exception exception) {
        String componentName = clazz.getSimpleName();
        usage.putIfAbsent(componentName, new TreeMap<>());

        Map<String, MethodUsageInfo> map = usage.get(componentName);
        map.putIfAbsent(method, new MethodUsageInfo(method));
        map.get(method).addRequest(exception, (int) elapsedTime);
    }

    @Synchronized
    public String dump() {
        long t1 = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("------ " + this.getClass().getSimpleName() + " ------\n");

        usage.forEach((component, data) -> {
            sb.append("\n[" + component + "]\n")
              .append(data.values().stream().map(MethodUsageInfo::toString).collect(Collectors.joining("\n")))
              .append("\n");
        });

        sb.append("------------------\n")
          .append("generated in " + (System.currentTimeMillis() - t1) + "ms\n");
        return sb.toString();
    }

    @Around("execution(* co.postscriptum.fs.FS.*(..)) || " +
            "execution(* co.postscriptum.email.EmailSender.*(..)) || " +
            "execution(* co.postscriptum.email.EmailDelivery.*(..)) || " +
            "execution(* co.postscriptum.email.EmailDiscWriter.*(..)) || " +
            "execution(* co.postscriptum.db.DB.*(..)) || " +
            "execution(* co.postscriptum.payment.BitcoinAddressGenerator.*(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        long t1 = System.currentTimeMillis();
        Exception thrown = null;
        try {
            return pjp.proceed();
        } catch (Exception e) {
            thrown = e;
            throw e;
        } finally {
            put(pjp.getTarget().getClass(), pjp.getSignature().getName(), System.currentTimeMillis() - t1, thrown);
        }
    }

}
