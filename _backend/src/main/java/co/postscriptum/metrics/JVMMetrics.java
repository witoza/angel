package co.postscriptum.metrics;

import co.postscriptum.internal.Utils;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

public class JVMMetrics {

    public String dump() {
        StringBuilder sb = new StringBuilder("------ " + this.getClass().getSimpleName() + " ------\n\n");

        sb.append("[jvm]\n");
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        sb.append("startup time: ")
          .append(Utils.format(ManagementFactory.getRuntimeMXBean().getStartTime())).append("\n")
          .append("active threads: ").append(threadMXBean.getThreadCount()).append("\n");

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

        sb.append("heap (used / max): ")
          .append(toMb(memoryMXBean.getHeapMemoryUsage().getUsed()))
          .append(" / ")
          .append(toMb(memoryMXBean.getHeapMemoryUsage().getMax())).append(" MB\n");

        sb.append("non heap (used / max): ")
          .append(toMb(memoryMXBean.getNonHeapMemoryUsage().getUsed()))
          .append(" / ")
          .append(toMb(memoryMXBean.getNonHeapMemoryUsage().getMax())).append(" MB\n");

        File file = new File(".").getAbsoluteFile();
        sb.append("\n[disc space]\n");
        sb.append("total: ").append(toMb(file.getTotalSpace())).append(" MB\n");
        sb.append("usable: ").append(toMb(file.getUsableSpace())).append(" MB\n");
        sb.append("free: ").append(toMb(file.getFreeSpace())).append(" MB\n");

        return sb.toString();
    }

    private long toMb(long bytes) {
        return bytes / 1024 / 1024;
    }

}
