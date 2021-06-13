package com.mwmd.core.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import javax.servlet.ServletOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.wrappers.SlingHttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a {@link SlingHttpServletResponse} instance to intercept write
 * operations. Doesn't write through to the containing
 * {@link SlingHttpServletResponse}, instead the text can be retrieved with
 * {@link #getText()} and then written to the original
 * {@link SlingHttpServletResponse} by the consuming code.
 */
public class ResponseBodyWrapper extends SlingHttpServletResponseWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(ResponseBodyWrapper.class);

    private ServletOutputStreamCapture out;

    private PrintWriter writer;

    public ResponseBodyWrapper(SlingHttpServletResponse resp) {
        super(resp);
        out = new ServletOutputStreamCapture();
        writer = new PrintWriter(out);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {

        return out;
    }

    @Override
    public PrintWriter getWriter() throws IOException {

        return writer;
    }

    @Override
    public int getBufferSize() {

        return 1024;
    }

    @Override
    public void flushBuffer() throws IOException {

        // void
    }

    public String getText() {

        writer.flush();
        byte[] text = out.getStream().toByteArray();
        String charset = getCharacterEncoding();
        if (StringUtils.isNotBlank(charset)) {
            try {
                return new String(text, charset);
            } catch (UnsupportedEncodingException e) {
                LOG.error("Invalid charset", e);
            }
        }
        return new String(text);
    }

}
