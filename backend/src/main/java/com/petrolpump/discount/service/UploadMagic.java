package com.petrolpump.discount.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;

/** Reject uploads that are not real images / PDFs (don't trust Content-Type alone). */
public final class UploadMagic {
    private UploadMagic() {}

    public static void requireImage(MultipartFile file) {
        byte[] head = readHead(file, 12);
        if (isJpeg(head) || isPng(head) || isWebp(head) || isGif(head)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, or WebP bill photos allowed");
    }

    public static void requirePdf(MultipartFile file) {
        byte[] head = readHead(file, 5);
        if (head.length >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') {
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a PDF file");
    }

    private static byte[] readHead(MultipartFile file, int n) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(n);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read upload");
        }
    }

    private static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }

    private static boolean isGif(byte[] b) {
        return b.length >= 6
                && b[0] == 'G' && b[1] == 'I' && b[2] == 'F'
                && b[3] == '8' && (b[4] == '7' || b[4] == '9') && b[5] == 'a';
    }

    private static boolean isWebp(byte[] b) {
        return b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }
}
