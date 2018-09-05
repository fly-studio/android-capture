package org.fly.protocol.http.request;

import org.fly.core.io.IOUtils;
import org.fly.core.text.HttpUtils;
import org.fly.protocol.cache.ByteBufferPool;
import org.fly.protocol.exception.RequestException;
import org.fly.protocol.http.Constant;
import org.fly.protocol.http.content.ContentType;
import org.fly.protocol.http.content.CookieHandler;
import org.fly.protocol.http.response.Response;
import org.fly.protocol.http.response.Status;
import org.fly.protocol.http.tempfiles.DefaultTempFileManagerFactory;
import org.fly.protocol.http.tempfiles.ITempFile;
import org.fly.protocol.http.tempfiles.ITempFileManager;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;

public class Request {

    private static final String TAG = Request.class.getSimpleName();

    public static final String POST_DATA = "postData";

    private static final int REQUEST_BUFFER_LEN = 512;

    private static final int MEMORY_STORE_LIMIT = 1024;

    /**
     * Apache 2.0, 2.2: 8K
     * nginx: 4K - 8K
     * IIS: varies by version, 8K - 16K
     * Tomcat: varies by version, 8K - 48K (?!)
     */
    public static final int BUFF_SIZE = 8 * 1024;

    public static final int MAX_HEADER_SIZE = 1024;

    private ITempFileManager tempFileManager = new DefaultTempFileManagerFactory().create();

    private String uri;

    private Method method;

    private Map<String, List<String>> params;

    private Map<String, String> headers;

    private CookieHandler cookies;

    private String queryParameterString;

    private String protocolVersion;

    private ByteBuffer cache;

    private HeaderParser headerParser;
    private BodyParser bodyParser = null;

    private boolean writeMode = true;

    public Request() {
        headerParser = new HeaderParser();
        cache = ByteBuffer.allocate(BUFF_SIZE);
    }

    public final Map<String, String> getHeaders() {
        return headers;
    }

    public final Method getMethod() {
        return method;
    }

    public final Map<String, List<String>> getParameters() {
        return params;
    }

    public final List<String> getParameter(String key) {
        return params.get(key);
    }

    public final List<String> inputs(String key)
    {
        return getParameter(key);
    }

    public final String input(String key) {
        List<String> value = inputs(key);

        return value == null || value.isEmpty() ? null : value.get(0);
    }

    public final String getUri() {
        return uri;
    }

    public String getUrl()
    {
        return "http://" + headers.get("host") + uri + (queryParameterString != null && !queryParameterString.isEmpty() ? "?" + queryParameterString : "");
    }

    public String getQueryParameterString() {
        return queryParameterString;
    }

    public boolean isHeaderComplete()
    {
        return headerParser.isComplete();
    }

    public boolean isBodyComplete() {
        return bodyParser != null && bodyParser.isComplete();
    }

    public long getBodySize()
    {
        return headerParser.isComplete() ? headerParser.getBodySize() : -1;
    }

    public void write(ByteBuffer byteBuffer) throws RequestException, IOException
    {
        append(byteBuffer);

        execute(byteBuffer);
    }

    private void append(ByteBuffer byteBuffer)
    {
        if (!writeMode)
            flush();

        cache.put(byteBuffer.duplicate());

        writeMode = true;
    }

    private void prepend(ByteBuffer byteBuffer)
    {
        flip();

        ByteBuffer buffer = null;
        if (cache.hasRemaining())
        {
            buffer = ByteBufferPool.acquire();
            buffer.put(cache);
        }

        cache.clear();
        writeMode = true;

        if (byteBuffer != null)
            cache.put(byteBuffer.duplicate());

        if (buffer != null) {
            cache.put(buffer);

            ByteBufferPool.release(buffer);
        }
    }

    private void flip()
    {
        if (writeMode)
            cache.flip();

        writeMode = false;
    }

    private void flush()
    {
        prepend(null);
    }


    private void execute(ByteBuffer byteBuffer) throws RequestException, IOException
    {
        flip();

        if (!isHeaderComplete())
        {
            headerParser.update(byteBuffer);

            if (headerParser.isComplete())
                bodyParser = new BodyParser(headerParser.getBodySize());

        }

        flip();

        if (isHeaderComplete() && !isBodyComplete())
        {
            if (bodyParser == null)
                throw new RequestException("Body parser lost");

            bodyParser.update();

            if (bodyParser.isComplete())
                tempFileManager.clear();
        }

    }

    private class HeaderParser {

        byte[] headerBuffer = new byte[BUFF_SIZE];
        private int headerEndpoint;
        private int rlen = 0;

        void update(ByteBuffer byteBuffer) throws IOException, RequestException
        {
            while(cache.hasRemaining() && BUFF_SIZE - rlen > 0)
            {
                int s = Math.min(BUFF_SIZE - rlen, cache.remaining());

                cache.get(headerBuffer, rlen, s);
                rlen += s;
            }

            headerEndpoint = findHeaderEndpoint(headerBuffer, rlen);

            // Read the first 8192 bytes.
            // The full header should fit in here.
            // Apache's default header limit is 8KB.
            // Do NOT assume that a single read will get the entire header
            // at once!

            if (rlen >= BUFF_SIZE && headerEndpoint == 0)
                throw new RequestException("Header size limit " + IOUtils.getFileSize(BUFF_SIZE));

            // Header Complete
            if (isComplete()) {

                // 多余部分是Body，回写到流开头
                if (headerEndpoint < rlen) {

                    ByteBuffer buffer = byteBuffer.duplicate();
                    buffer.position(buffer.limit() - rlen + headerEndpoint);
                    rlen = headerEndpoint;

                    prepend(buffer);
                }

                parseHeader();
            }
        }

        boolean isComplete()
        {
            return headerEndpoint > 0;
        }

        /**
         * Find byte index separating header from body. It must be the last byte of
         * the first two sequential new lines.
         */
        private int findHeaderEndpoint(final byte[] buf, int rlen) {

            int splitbyte = 0;
            while (splitbyte + 1 < rlen) {

                // RFC2616
                if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n' && splitbyte + 3 < rlen && buf[splitbyte + 2] == '\r' && buf[splitbyte + 3] == '\n') {
                    return splitbyte + 4;
                }

                // tolerance
                if (buf[splitbyte] == '\n' && buf[splitbyte + 1] == '\n') {
                    return splitbyte + 2;
                }
                splitbyte++;
            }

            return 0;
        }

        private void parseHeader() throws RequestException
        {
            params = new HashMap<>();
            if (null == headers) {
                headers = new HashMap<>();
            } else {
                headers.clear();
            }

            // Create a BufferedReader for parsing the header.
            BufferedReader hin = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(headerBuffer, 0, rlen)));
            // Decode the header into params and header java properties
            Map<String, String> pre = new HashMap<>();

            decodeHeader(hin, pre, params, headers);

            method = Method.lookup(pre.get("method"));
            if (method == null) {
                throw new RequestException("BAD REQUEST: Syntax error. HTTP verb " + pre.get("method") + " unhandled.");
            }

            uri = pre.get("uri");

            cookies = new CookieHandler(headers);
        }

        /**
         * Deduce body length in bytes. Either from "content-length" header or read
         * bytes.
         */
        long getBodySize() {
            if (headers.containsKey("content-length")) {
                return Long.parseLong(headers.get("content-length"));
            } else if (headerEndpoint < rlen) {
                return rlen - headerEndpoint;
            }

            return 0;
        }

        /**
         * Decodes the sent headers and loads the data into Key/value pairs
         */
        private void decodeHeader(BufferedReader in,
                                  Map<String, String> pre,
                                  Map<String, List<String>> params,
                                  Map<String, String> headers) throws RequestException {
            try {
                // Read the request line
                String inLine = in.readLine();
                if (inLine == null) {
                    return;
                }

                StringTokenizer st = new StringTokenizer(inLine);
                if (!st.hasMoreTokens()) {
                    throw new RequestException("BAD REQUEST: Syntax error. \""+inLine+"\" Usage: GET /example/file.html");
                }

                pre.put("method", st.nextToken());

                if (!st.hasMoreTokens()) {
                    throw new RequestException("BAD REQUEST: Missing URI. \""+inLine+"\" Usage: GET /example/file.html");
                }

                String uri = st.nextToken();

                // Decode parameters from the URI
                int qmi = uri.indexOf('?');
                if (qmi >= 0) {
                    decodeParams(uri.substring(qmi + 1), params);
                    uri = HttpUtils.decodePercent(uri.substring(0, qmi));
                } else {
                    uri = HttpUtils.decodePercent(uri);
                }

                // If there's another token, its protocol version,
                // followed by HTTP headers.
                // NOTE: this now forces header names lower case since they are
                // case insensitive and vary by client.
                if (st.hasMoreTokens()) {
                    protocolVersion = st.nextToken();
                } else {
                    protocolVersion = "HTTP/1.1";
                }

                String line = in.readLine();
                while (line != null && !line.trim().isEmpty()) {
                    int p = line.indexOf(':');
                    if (p >= 0) {
                        headers.put(line.substring(0, p).trim().toLowerCase(Locale.US), line.substring(p + 1).trim());
                    }
                    line = in.readLine();
                }

                pre.put("uri", uri);
            } catch (IOException ioe) {
                throw new RequestException("SERVER INTERNAL ERROR: IOException: " + ioe.getMessage(), ioe);
            }
        }
    }

    private class BodyParser {

        private ByteArrayOutputStream baos = null;
        private DataOutput requestDataOutput;
        private long size;
        private RandomAccessFile randomAccessFile = null;
        private Map<String, String> files;

        BodyParser(long size) {

            this.size = size;

            // Store the request in memory or a file, depending on size
            if (size < MEMORY_STORE_LIMIT) {
                baos = new ByteArrayOutputStream();
                requestDataOutput = new DataOutputStream(baos);
            } else {
                randomAccessFile = getTmpBucket();
                requestDataOutput = randomAccessFile;
            }
        }

        void update() throws IOException, RequestException
        {
            // Read all the body and write it to request_data_output
            while(cache.hasRemaining() && size > 0)
            {
                byte[] buf = new byte[REQUEST_BUFFER_LEN];

                int rlen = (int) Math.min(Math.min(size, REQUEST_BUFFER_LEN), cache.remaining());
                cache.get(buf, 0, rlen);
                size -= rlen;
                if (rlen > 0) {
                    requestDataOutput.write(buf, 0, rlen);
                }
            }

            if (isComplete())
            {
                files = new HashMap<>();
                parseBody(files);
            }

        }

        boolean isComplete()
        {
            return size == 0;
        }

        private void parseBody(Map<String, String> files) throws IOException, RequestException {

            try {

                ByteBuffer fbuf;
                if (baos != null) {
                    fbuf = ByteBuffer.wrap(baos.toByteArray(), 0, baos.size());
                } else {
                    fbuf = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, randomAccessFile.length());
                    randomAccessFile.seek(0);
                }

                // If the method is POST, there may be parameters
                // in data section, too, read it:
                if (Method.POST.equals(method)) {
                    ContentType contentType = new ContentType(headers.get("content-type"));
                    if (contentType.isMultipart()) {
                        String boundary = contentType.getBoundary();
                        if (boundary == null) {
                            throw new RequestException("BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html");
                        }
                        decodeMultipartFormData(contentType, fbuf, params, files);
                    } else {
                        byte[] postBytes = new byte[fbuf.remaining()];
                        fbuf.get(postBytes);
                        String postLine = new String(postBytes, contentType.getEncoding()).trim();
                        // Handle application/x-www-form-urlencoded
                        if ("application/x-www-form-urlencoded".equalsIgnoreCase(contentType.getContentType())) {
                            decodeParams(postLine, params);
                        } else if (postLine.length() != 0) {
                            // Special case for raw POST data => create a
                            // special files entry "postData" with raw content
                            // data
                            files.put(POST_DATA, postLine);
                        }
                    }
                } else if (Method.PUT.equals(method)) {
                    files.put("content", saveTmpFile(fbuf, 0, fbuf.limit(), null));
                }
            } finally {
                IOUtils.safeClose(randomAccessFile);
            }
        }

        /**
         * Decodes the Multipart Body data and put it into Key/Value pairs.
         */
        private void decodeMultipartFormData(ContentType contentType,
                                             ByteBuffer fbuf,
                                             Map<String, List<String>> params,
                                             Map<String, String> files) throws RequestException {
            int pcount = 0;
            try {
                int[] boundaryIdxs = getBoundaryPositions(fbuf, contentType.getBoundary().getBytes());
                if (boundaryIdxs.length < 2) {
                    throw new RequestException("BAD REQUEST: Content type is multipart/form-data but contains less than two boundary strings.");
                }

                byte[] partHeaderBuff = new byte[MAX_HEADER_SIZE];
                for (int boundaryIdx = 0; boundaryIdx < boundaryIdxs.length - 1; boundaryIdx++) {
                    fbuf.position(boundaryIdxs[boundaryIdx]);
                    int len = (fbuf.remaining() < MAX_HEADER_SIZE) ? fbuf.remaining() : MAX_HEADER_SIZE;
                    fbuf.get(partHeaderBuff, 0, len);
                    BufferedReader in =
                            new BufferedReader(new InputStreamReader(new ByteArrayInputStream(partHeaderBuff, 0, len), Charset.forName(contentType.getEncoding())), len);

                    int headerLines = 0;
                    // First line is boundary string
                    String mpline = in.readLine();
                    headerLines++;
                    if (mpline == null || !mpline.contains(contentType.getBoundary())) {
                        throw new RequestException("BAD REQUEST: Content type is multipart/form-data but chunk does not start with boundary.");
                    }

                    String partName = null, fileName = null, partContentType = null;
                    // Parse the reset of the header lines
                    mpline = in.readLine();
                    headerLines++;
                    while (mpline != null && mpline.trim().length() > 0) {
                        Matcher matcher = Constant.CONTENT_DISPOSITION_PATTERN.matcher(mpline);
                        if (matcher.matches()) {
                            String attributeString = matcher.group(2);
                            matcher = Constant.CONTENT_DISPOSITION_ATTRIBUTE_PATTERN.matcher(attributeString);
                            while (matcher.find()) {
                                String key = matcher.group(1);
                                if ("name".equalsIgnoreCase(key)) {
                                    partName = matcher.group(2);
                                } else if ("filename".equalsIgnoreCase(key)) {
                                    fileName = matcher.group(2);
                                    // add these two line to support multiple
                                    // files uploaded using the same field Id
                                    if (!fileName.isEmpty()) {
                                        if (pcount > 0)
                                            partName = partName + String.valueOf(pcount++);
                                        else
                                            pcount++;
                                    }
                                }
                            }
                        }
                        matcher = Constant.CONTENT_TYPE_PATTERN.matcher(mpline);
                        if (matcher.matches()) {
                            partContentType = matcher.group(2).trim();
                        }
                        mpline = in.readLine();
                        headerLines++;
                    }
                    int partHeaderLength = 0;
                    while (headerLines-- > 0) {
                        partHeaderLength = scipOverNewLine(partHeaderBuff, partHeaderLength);
                    }
                    // Read the part data
                    if (partHeaderLength >= len - 4) {
                        throw new RequestException("Multipart header size exceeds MAX_HEADER_SIZE.");
                    }
                    int partDataStart = boundaryIdxs[boundaryIdx] + partHeaderLength;
                    int partDataEnd = boundaryIdxs[boundaryIdx + 1] - 4;

                    fbuf.position(partDataStart);

                    List<String> values = params.get(partName);
                    if (values == null) {
                        values = new ArrayList<String>();
                        params.put(partName, values);
                    }

                    if (partContentType == null) {
                        // Read the part into a string
                        byte[] data_bytes = new byte[partDataEnd - partDataStart];
                        fbuf.get(data_bytes);

                        values.add(new String(data_bytes, contentType.getEncoding()));
                    } else {
                        // Read it into a file
                        String path = saveTmpFile(fbuf, partDataStart, partDataEnd - partDataStart, fileName);
                        if (!files.containsKey(partName)) {
                            files.put(partName, path);
                        } else {
                            int count = 2;
                            while (files.containsKey(partName + count)) {
                                count++;
                            }
                            files.put(partName + count, path);
                        }
                        values.add(fileName);
                    }
                }
            } catch (RequestException re) {
                throw re;
            } catch (Exception e) {
                throw new RequestException(e);
            }
        }

        private int scipOverNewLine(byte[] partHeaderBuff, int index) {
            while (partHeaderBuff[index] != '\n') {
                index++;
            }
            return ++index;
        }

        /**
         * Retrieves the content of a sent file and saves it to a temporary file.
         * The full path to the saved file is returned.
         */
        private String saveTmpFile(ByteBuffer b, int offset, int len, String filename_hint) {
            String path = "";
            if (len > 0) {
                FileOutputStream fileOutputStream = null;
                try {
                    ITempFile tempFile = tempFileManager.createTempFile(filename_hint);
                    ByteBuffer src = b.duplicate();
                    fileOutputStream = new FileOutputStream(tempFile.getName());
                    FileChannel dest = fileOutputStream.getChannel();
                    src.position(offset).limit(offset + len);
                    dest.write(src.slice());
                    path = tempFile.getName();
                } catch (Exception e) { // Catch exception if any
                    throw new Error(e); // we won't recover, so throw an error
                } finally {
                    try
                    {
                        IOUtils.safeClose(fileOutputStream);
                    }catch (Exception e)
                    {

                    }
                }
            }
            return path;
        }

        /**
         * Find the byte positions where multipart boundaries start. This reads a
         * large block at a time and uses a temporary buffer to optimize (memory
         * mapped) file access.
         */
        private int[] getBoundaryPositions(ByteBuffer b, byte[] boundary) {
            int[] res = new int[0];
            if (b.remaining() < boundary.length) {
                return res;
            }

            int search_window_pos = 0;
            byte[] search_window = new byte[4 * 1024 + boundary.length];

            int first_fill = (b.remaining() < search_window.length) ? b.remaining() : search_window.length;
            b.get(search_window, 0, first_fill);
            int new_bytes = first_fill - boundary.length;

            do {
                // Search the search_window
                for (int j = 0; j < new_bytes; j++) {
                    for (int i = 0; i < boundary.length; i++) {
                        if (search_window[j + i] != boundary[i])
                            break;
                        if (i == boundary.length - 1) {
                            // Match found, add it to results
                            int[] new_res = new int[res.length + 1];
                            System.arraycopy(res, 0, new_res, 0, res.length);
                            new_res[res.length] = search_window_pos + j;
                            res = new_res;
                        }
                    }
                }
                search_window_pos += new_bytes;

                // Copy the end of the buffer to the start
                System.arraycopy(search_window, search_window.length - boundary.length, search_window, 0, boundary.length);

                // Refill search_window
                new_bytes = search_window.length - boundary.length;
                new_bytes = (b.remaining() < new_bytes) ? b.remaining() : new_bytes;
                b.get(search_window, boundary.length, new_bytes);
            } while (new_bytes > 0);
            return res;
        }

        private RandomAccessFile getTmpBucket() {
            try {
                ITempFile tempFile = tempFileManager.createTempFile(null);
                return new RandomAccessFile(tempFile.getName(), "rw");
            } catch (Exception e) {
                throw new Error(e); // we won't recover, so throw an error
            }
        }
    }




    public void response(String content, OutputStream outputStream) throws IOException
    {
        Response response = Response.newFixedLengthResponse(Status.INTERNAL_ERROR, Constant.MIME_HTML, content);
        String connection = this.headers.get("connection");
        boolean keepAlive = "HTTP/1.1".equals(protocolVersion) && (connection == null || !connection.matches("(?i).*close.*"));

        String acceptEncoding = this.headers.get("accept-encoding");
        this.cookies.unloadQueue(response);
        response.setRequestMethod(this.method);
        if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
            response.setUseGzip(false);
        }
        response.setKeepAlive(keepAlive);
        response.send(outputStream);
        IOUtils.safeClose(outputStream);

        if (!keepAlive || response.isCloseConnection()) {
            throw new SocketException("Http Shutdown");
        }
    }


    /**
     * Decodes parameters in percent-encoded URI-format ( e.g.
     * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given Map.
     */
    private void decodeParams(String params, Map<String, List<String>> p) throws UnsupportedEncodingException {
        if (params == null) {
            queryParameterString = "";
            return;
        }

        queryParameterString = params;
        StringTokenizer st = new StringTokenizer(params, "&");
        while (st.hasMoreTokens()) {
            String e = st.nextToken();
            int sep = e.indexOf('=');
            String key = null;
            String value = null;

            if (sep >= 0) {
                key = HttpUtils.decodePercent(e.substring(0, sep)).trim();
                value = HttpUtils.decodePercent(e.substring(sep + 1));
            } else {
                key = HttpUtils.decodePercent(e).trim();
                value = "";
            }

            List<String> values = p.get(key);
            if (values == null) {
                values = new ArrayList<>();
                p.put(key, values);
            }

            values.add(value);
        }
    }
}
