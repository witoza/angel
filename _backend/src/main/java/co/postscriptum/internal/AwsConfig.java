package co.postscriptum.internal;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Builder
@AllArgsConstructor
@Value
public class AwsConfig {

    private String accessKeyId;

    private String secretAccessKey;

    private String sesRegion;

    private String s3Bucket;

    private String sqsRegion;

    private String sqsQueueName;

    public AWSCredentialsProvider awsCredentialsProvider() {
        return new AWSStaticCredentialsProvider(new BasicAWSCredentials(getAccessKeyId(), getSecretAccessKey()));
    }

}
