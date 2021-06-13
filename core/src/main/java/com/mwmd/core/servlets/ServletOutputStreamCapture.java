package com.mwmd.core.servlets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mimics a {@link ServletOutputStream} but instead writes to its own internal
 * {@link ByteArrayOutputStream} so output can be retrieved multiple times and
 * manipulated.
 */
public class ServletOutputStreamCapture extends ServletOutputStream {

    private static final Logger LOG = LoggerFactory.getLogger(ServletOutputStreamCapture.class);

    private ByteArrayOutputStream out = new ByteArrayOutputStream(1024);

    @Override
    public boolean isReady() {

        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {

        // void
    }

    @Override
    public void write(int b) throws IOException {

        LOG.info("write {}", b);

        out.write(b);
    }

    public ByteArrayOutputStream getStream() {

        return out;
    }

}
