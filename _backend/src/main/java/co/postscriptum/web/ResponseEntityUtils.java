package co.postscriptum.web;

import com.google.zxing.WriterException;
import lombok.experimental.UtilityClass;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@UtilityClass
public class ResponseEntityUtils {

    public ResponseEntity<InputStreamResource> asAttachment(InputStream inputStream, String mime, String name) {
        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_TYPE, mime)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(new InputStreamResource(inputStream));
    }

    public ResponseEntity<InputStreamResource> asInline(InputStream inputStream, String mime) {
        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_TYPE, mime)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline;")
                .body(new InputStreamResource(inputStream));
    }

    public ResponseEntity<InputStreamResource> asPng(BufferedImage image) throws WriterException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return asInline(new ByteArrayInputStream(baos.toByteArray()), "image/png");
    }

}
