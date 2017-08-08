package co.postscriptum.metrics;

import lombok.Synchronized;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;

public class RequestsByTimeMetrics {

    private final LinkedList<DataHolder> last60Minute = new LinkedList<>();

    private final LinkedList<DataHolder> last24Hours = new LinkedList<>();

    private final LinkedList<DataHolder> last30Days = new LinkedList<>();

    @Synchronized
    public void newRequest() {
        newRequest(last60Minute, Calendar.MINUTE, 60);
        newRequest(last24Hours, Calendar.HOUR_OF_DAY, 24);
        newRequest(last30Days, Calendar.DAY_OF_MONTH, 30);
    }

    private void newRequest(LinkedList<DataHolder> lastList, int timeType, int maxElements) {
        if (lastList.isEmpty()) {
            lastList.addLast(new DataHolder());
        } else {

            DataHolder dataHolder = lastList.getLast();

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(dataHolder.timestamp);

            if (Calendar.getInstance().get(timeType) == calendar.get(timeType)) {
                dataHolder.value++;
            } else {
                lastList.addLast(new DataHolder());
            }
        }

        if (lastList.size() > maxElements) {
            lastList.removeFirst();
        }
    }

    @Synchronized
    public String dump() {

        StringBuilder sb = new StringBuilder();
        sb.append("[requests by time]");

        sb.append("\nlast 60 minutes:\n");
        for (DataHolder dh : last60Minute) {
            String df = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(dh.timestamp));
            sb.append(" " + df + "m -> " + dh.value).append("\n");
        }

        sb.append("\nlast 24 hours:\n");
        for (DataHolder dh : last24Hours) {
            String df = new SimpleDateFormat("yyyy-MM-dd HH").format(new Date(dh.timestamp));
            sb.append(" " + df + "h -> " + dh.value).append("\n");
        }

        sb.append("\nlast 30 days:\n");
        for (DataHolder dh : last30Days) {
            String df = new SimpleDateFormat("yyyy-MM-dd").format(new Date(dh.timestamp));
            sb.append(" " + df + " -> " + dh.value).append("\n");
        }

        return sb.toString();
    }

    private static class DataHolder {

        final long timestamp = System.currentTimeMillis();

        int value = 1;

    }

}