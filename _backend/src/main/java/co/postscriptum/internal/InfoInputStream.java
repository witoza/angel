package co.postscriptum.internal;

import lombok.Getter;
import org.apache.commons.codec.binary.Hex;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class InfoInputStream extends FilterInputStream {

    @Getter
    private long size = 0;

    public InfoInputStream(InputStream is) {
        super(new DigestInputStream(is, sha1md()));
    }

    private static MessageDigest sha1md() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Can't find SHA-1 MessageDigest", e);
        }
    }

    @Override
    public int read() throws IOException {
        int ch = super.read();
        if (ch != -1) {
            size++;
        }
        return ch;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = super.read(b, off, len);
        if (result != -1) {
            size += result;
        }
        return result;
    }

    public String getSha1() {
        return Hex.encodeHexString(((DigestInputStream) in).getMessageDigest().digest());
    }

}
