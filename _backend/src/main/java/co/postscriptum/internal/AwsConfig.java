package co.postscriptum.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@AllArgsConstructor
@Getter
public class AwsConfig {

    private final String accessKeyId;

    private final String secretAccessKey;

    private final String sesRegion;

    private final String s3Bucket;

    private final String sqsRegion;

    private final String sqsQueueName;
}
