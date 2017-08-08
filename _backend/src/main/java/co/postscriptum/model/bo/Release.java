package co.postscriptum.model.bo;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class Release {

    private long releaseTime;

    private List<ReleaseItem> items;

}
