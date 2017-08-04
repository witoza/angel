package co.postscriptum.metrics;

import co.postscriptum.internal.Utils;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

public class JVMMetrics {

    public String dump() {
        StringBuilder sb = new StringBuilder();

        sb.append("[jvm]\n");

        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        sb.append("startup time = ")
          .append(Utils.format(ManagementFactory.getRuntimeMXBean().getStartTime())).append("\n")
          .append("active threads = ").append(threadMXBean.getThreadCount()).append("\n");

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        sb.append("heap used / max [MB] = ")
          .append(toMb(memoryMXBean.getHeapMemoryUsage().getUsed()))
          .append(" / ")
          .append(toMb(memoryMXBean.getHeapMemoryUsage().getMax())).append("\n");

        sb.append("nonHeap used / max [MB] = ")
          .append(toMb(memoryMXBean.getNonHeapMemoryUsage().getUsed()))
          .append(" / ")
          .append(toMb(memoryMXBean.getNonHeapMemoryUsage().getMax())).append("\n");

        return sb.toString();

    }

    private long toMb(long bytes) {
        return bytes / 1024 / 1024;
    }

}
