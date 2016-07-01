package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Release {

    private final long releaseTime;
    private final List<ReleaseItem> items;

}
