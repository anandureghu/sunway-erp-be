package com.erp.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class InMemoryMultipartFile implements MultipartFile {

    private final byte[] content;
    private final String filename;
    private final String contentType;

    public InMemoryMultipartFile(
            byte[] content,
            String filename,
            String contentType
    ) {
        this.content = content;
        this.filename = filename;
        this.contentType = contentType;
    }

    @Override
    public String getName() {
        return filename;
    }

    @Override
    public String getOriginalFilename() {
        return filename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(java.io.File dest) {
        throw new UnsupportedOperationException();
    }
}
