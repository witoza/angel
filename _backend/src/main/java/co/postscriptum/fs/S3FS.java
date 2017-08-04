package co.postscriptum.fs;

import co.postscriptum.RuntimeEnvironment;
import co.postscriptum.internal.AwsConfig;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TeeInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Profile(value = {"prod", "cert"})
@Component
public class S3FS implements FS {

    @Autowired
    private RuntimeEnvironment env;

    @Autowired
    private AwsConfig awsConfig;

    private AmazonS3 s3client;

    @Value("${my.s3fs.cache_dir:.s3fscache/}")
    private String cacheDir;

    private String getAbsolutePath(String relPath) {
        return "db_" + env + "/" + relPath;
    }

    private void createCacheDir() throws IOException {

        Path cacheDirPath = Paths.get(cacheDir);

        log.info("Creating file cache dir: {}", cacheDirPath);

        Files.createDirectories(cacheDirPath);
    }

    @PostConstruct
    public void init() throws IOException {
        createCacheDir();

        log.info("Connecting to AWS S3 ...");

        s3client = AmazonS3Client.builder()
                                 .withCredentials(awsConfig.awsCredentialsProvider())
                                 .build();

        log.info("Connected to AWS S3");
    }

    @Override
    public void save(String relPath, InputStream is) throws IOException {

        String absPath = getAbsolutePath(relPath);

        log.info("Saving inputStream in object {}", absPath);

        ObjectMetadata metadata = new ObjectMetadata();

        try {
            s3client.putObject(awsConfig.getS3Bucket(), absPath, is, metadata);
        } catch (Exception e) {
            throw new IOException("Can't fulfill S3 putObject", e);
        }
    }

    @Override
    public void save(String relPath, String data) throws IOException {

        String absPath = getAbsolutePath(relPath);

        log.info("Saving string in object {}", absPath);

        try {
            s3client.putObject(awsConfig.getS3Bucket(), absPath, data);
        } catch (Exception e) {
            throw new IOException("Can't fulfill S3 putObject", e);
        }
    }

    @Override
    public InputStream load(String relPath) throws IOException {

        String absPath = getAbsolutePath(relPath);

        log.info("Loading direct from s3 {}", absPath);

        try {
            return s3client.getObject(awsConfig.getS3Bucket(), absPath).getObjectContent();
        } catch (Exception e) {
            throw new IOException("Can't fulfill S3 getObject", e);
        }
    }

    @Override
    public InputStream load(String relPath, boolean allowCache) throws IOException {
        log.info("Loading object {}", relPath);

        if (!allowCache) {
            return load(relPath);
        }

        File cachedFile = new File(cacheDir + relPath.replace("/", "_"));
        if (cachedFile.exists()) {

            log.info("Returning cached file");
            try {
                return new FileInputStream(cachedFile);
            } catch (Exception e) {
                log.error("Can't get s3 object from cache, returning original stream", e);
                return load(relPath);
            }

        }

        try {
            log.info("Returning original stream and caching it");

            FileOutputStream cached = new FileOutputStream(cachedFile) {
                @Override
                public void close() throws IOException {
                    super.close();
                    log.info("File {} has been successfully cached to {}", relPath, cachedFile.getPath());
                }
            };

            return new TeeInputStream(load(relPath), cached, true);
        } catch (Exception e) {
            log.error("Can't put s3 object into cache, returning original stream", e);
            return load(relPath);
        }

    }

    @Override
    public void remove(String relPath) throws IOException {

        String absPath = getAbsolutePath(relPath);

        log.info("Removing object {}", absPath);

        try {
            s3client.deleteObject(awsConfig.getS3Bucket(), absPath);
        } catch (Exception e) {
            throw new IOException("Can't fulfill S3 deleteObject", e);
        }

    }

}
