package co.postscriptum.fs;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Profile("dev")
@Component
public class HDFS implements FS {

    @Value("${my.hdfs.db_path}")
    private String dbPath;

    private String getAbsolutePath(String relativePath) {
        return dbPath + "/" + relativePath;
    }

    @Override
    public void save(String relPath, InputStream is) throws IOException {

        File file = new File(getAbsolutePath(relPath));

        if (!file.getParentFile().exists()) {
            Path dir = file.getParentFile().toPath();
            log.info("Parent directory: {} not exists - creating", dir);
            Files.createDirectories(dir);
        }

        log.info("Saving stream to file: {}", file.getAbsoluteFile());
        try (FileOutputStream fos = new FileOutputStream(file)) {
            IOUtils.copy(is, fos);
        }
    }

    @Override
    public void remove(String relPath) throws IOException {

        String absPath = getAbsolutePath(relPath);

        log.info("Removing file: {}", absPath);

        Files.delete(Paths.get(absPath));

    }

    @Override
    public InputStream load(String relPath, boolean allowCache) throws IOException {

        String absPath = getAbsolutePath(relPath);

        log.info("Loading file: {}", absPath);

        return new FileInputStream(absPath);
    }

}
