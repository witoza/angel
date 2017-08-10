package co.postscriptum.test_data;

import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class SimpleMultipartFile implements MultipartFile {

    private final String directory;

    private final String originalFileName;

    public SimpleMultipartFile(String directory, String originalFileName) {
        this.directory = directory;
        this.originalFileName = originalFileName;
    }

    private String mime(String fileName) {
        if (fileName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (fileName.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (fileName.endsWith(".webm")) {
            return "video/webm";
        } else if (fileName.endsWith(".mp4")) {
            return "video/mp4";
        } else if (fileName.endsWith(".jpg")) {
            return "image/jpg";
        }
        return "application/octet-stream";
    }

    @Override
    public String getName() {
        throw new RuntimeException("not implemented");
    }

    @Override
    public String getOriginalFilename() {
        return originalFileName;
    }

    @Override
    public String getContentType() {
        return mime(originalFileName);
    }

    @Override
    public boolean isEmpty() {
        throw new RuntimeException("not implemented");
    }

    @Override
    public long getSize() {
        return 0;
    }

    @Override
    public byte[] getBytes() throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(directory + originalFileName);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
        throw new RuntimeException("not implemented");
    }

}
