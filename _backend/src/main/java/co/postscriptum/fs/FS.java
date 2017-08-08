package co.postscriptum.fs;

import com.gc.iotools.stream.os.OutputStreamToInputStream;
import org.apache.commons.io.input.ReaderInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

public interface FS {

    default void save(String relPath, String data) throws IOException {
        save(relPath, new ReaderInputStream(new StringReader(data), StandardCharsets.UTF_8));
    }

    InputStream load(String relPath, boolean allowCache) throws IOException;

    default InputStream load(String relPath) throws IOException {
        return load(relPath, false);
    }

    default OutputStreamToInputStream<Void> saveTo(String relPath) {
        return new OutputStreamToInputStream<Void>() {
            @Override
            protected Void doRead(InputStream istream) throws IOException {
                save(relPath, istream);
                return null;
            }

        };
    }

    void save(String relPath, InputStream is) throws IOException;

    void remove(String relPath) throws IOException;

}
