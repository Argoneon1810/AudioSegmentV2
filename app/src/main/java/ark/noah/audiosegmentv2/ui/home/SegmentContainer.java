package ark.noah.audiosegmentv2.ui.home;

import android.util.Pair;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class SegmentContainer {
    private int id;
    private int audio_id;
    private long start_timestamp;
    private long end_timestamp;
    private String description;
    private int condition;

    public static final int CONDITION_ONOFF = 0b1000;
    public static final int CONDITION_LOOPYN = 0b0100;

    private static final String DATE_FORMAT = "%d:%02d:%02d";

    public SegmentContainer() {
        id = -1;
        audio_id = -1;
        start_timestamp = 0;
        end_timestamp = 0;
        description = "";
        condition = -1;
    }
    public SegmentContainer(int id, int audio_id, long start_timestamp, long end_timestamp, String description, int condition) {
        this.id              = id             ;
        this.audio_id        = audio_id       ;
        this.start_timestamp = start_timestamp;
        this.end_timestamp   = end_timestamp  ;
        this.description     = description    ;
        this.condition       = condition      ;
    }

    public void mergeTargetToSelf(SegmentContainer other) {
        long smallest = Long.min(this.start_timestamp, other.start_timestamp);
        long biggest = Long.max(this.end_timestamp, other.end_timestamp);
        this.description += other.description;
        this.start_timestamp = smallest;
        this.end_timestamp = biggest;
    }

    public Pair<SegmentContainer, SegmentContainer> split(long cutpoint) {
        if (cutpoint >= this.getEnd_timestamp() || cutpoint <= this.getStart_timestamp())
            return null;

        SegmentContainer first = new SegmentContainer();
        SegmentContainer second = new SegmentContainer();

        first.setAudio_id(this.getAudio_id());
        second.setAudio_id(this.getAudio_id());

        first.setStart_timestamp(this.getStart_timestamp());
        first.setEnd_timestamp(cutpoint);
        second.setStart_timestamp(cutpoint + 1);
        second.setEnd_timestamp(this.getEnd_timestamp());

        first.setCondition(this.getCondition());
        second.setCondition(this.getCondition());

        first.setDescription(this.getDescription() + "_1");
        second.setDescription(this.getDescription() + "_2");

        return new Pair<>(first, second);
    }

    //region getter setter
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAudio_id() { return audio_id; }
    public void setAudio_id(int audio_id) { this.audio_id = audio_id; }

    public long getStart_timestamp() { return start_timestamp; }
    public String getStart_timestampAsString() { return formatLongTimeToString(start_timestamp); }
    public void setStart_timestamp(long start_timestamp) { this.start_timestamp = start_timestamp; }
    public void setStart_timestampFromString(String timeInString) { this.start_timestamp = formatStringTimeToLong(timeInString); }

    public long getEnd_timestamp() { return end_timestamp; }
    public String getEnd_timestampAsString() { return formatLongTimeToString(end_timestamp); }
    public void setEnd_timestamp(long end_timestamp) { this.end_timestamp = end_timestamp; }
    public void setEnd_timestampFromString(String timeInString) { this.end_timestamp = formatStringTimeToLong(timeInString); }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getCondition() { return condition; }
    public void setCondition(int condition) { this.condition = condition; }
    //endregion

    //region condition check
    public static boolean isLooping(int condition) { return (condition & CONDITION_LOOPYN) == CONDITION_LOOPYN; }
    public static boolean isOn(int condition) { return (condition & CONDITION_ONOFF) == CONDITION_ONOFF; }
    public boolean isLooping() { return isLooping(condition); }
    public boolean isOn() { return isOn(condition); }

    public boolean isConsequentOrAdjacent(SegmentContainer other) { return isOverlap(other) | isAdjacent(other); }
    private boolean isOverlap(SegmentContainer other) {
        return isInRange(this, other.start_timestamp) || isInRange(this, other.end_timestamp) || isInRange(other, this.start_timestamp) || isInRange(other, this.end_timestamp);
    }
    private boolean isAdjacent(SegmentContainer other) {
        return ((this.start_timestamp - 1) == other.end_timestamp) || ((other.start_timestamp - 1) == this.end_timestamp);
    }
    public static boolean isInRange(SegmentContainer target, long time) {
        return target.start_timestamp <= time && time <= target.end_timestamp;
    }
    //endregion

    //region Time Format
    public static String formatLongTimeToString(long time) {
        return String.format(
                Locale.ENGLISH,
                DATE_FORMAT,
                TimeUnit.MILLISECONDS.toHours(time),
                TimeUnit.MILLISECONDS.toMinutes(time) - TimeUnit.HOURS  .toMinutes(TimeUnit.MILLISECONDS.toHours(time)),
                TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))
        );
    }
    public static long formatStringTimeToLong(String timeInString) {
        String[] vals = timeInString.split(":");
        return formatTimeElementsToLong(
                Long.parseLong(vals[0]),
                Long.parseLong(vals[1]),
                Long.parseLong(vals[2]),
                0
        );
    }
    public static long formatTimeElementsToLong(long h, long m, long s, long ms) {
        return h*3600000 + m*60000 + s*1000 + ms;
    }
    //endregion
}
