package org.fly.android.localvpn.structs;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

public class HttpUtils {
    /**
     * Decode percent encoded <code>String</code> values.
     *
     * @param str
     *            the percent encoded <code>String</code>
     * @return expanded form of the input, for example "foo%20bar" becomes
     *         "foo bar"
     */
    public static String decodePercent(String str) throws UnsupportedEncodingException {
        return URLDecoder.decode(str, "UTF8");
    }

    /**
     * Decode parameters from a URL, handing the case where a single parameter
     * name might have been supplied several times, by return lists of values.
     * In general these lists will contain a single element.
     *
     * @param queryString
     *            a query string pulled from the URL.
     * @return a map of <code>String</code> (parameter name) to
     *         <code>List&lt;String&gt;</code> (a list of the values supplied).
     */
    protected static Map<String, List<String>> decodeParameters(String queryString) throws UnsupportedEncodingException {
        Map<String, List<String>> params = new HashMap<>();
        if (queryString != null) {
            StringTokenizer st = new StringTokenizer(queryString, "&");
            while (st.hasMoreTokens()) {
                String e = st.nextToken();
                int sep = e.indexOf('=');
                String propertyName = sep >= 0 ? decodePercent(e.substring(0, sep)).trim() : decodePercent(e).trim();
                if (!params.containsKey(propertyName)) {
                    params.put(propertyName, new ArrayList<String>());
                }
                String propertyValue = sep >= 0 ? decodePercent(e.substring(sep + 1)) : null;
                if (propertyValue != null) {
                    params.get(propertyName).add(propertyValue);
                }
            }
        }
        return params;
    }

    protected static Map<String, String> MIME_TYPES;

    public static Map<String, String> mimeTypes() {
        if (MIME_TYPES == null) {
            MIME_TYPES = new HashMap<>();

            try {
                loadMimeTypes(MIME_TYPES, "META-INF/nanohttpd/default-mimetypes.properties");
                loadMimeTypes(MIME_TYPES, "META-INF/nanohttpd/mimetypes.properties");
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return MIME_TYPES;
    }

    @SuppressWarnings({
            "unchecked",
            "rawtypes"
    })
    private static void loadMimeTypes(Map<String, String> result, String resourceName) throws IOException {

        Enumeration<URL> resources = HttpUtils.class.getClassLoader().getResources(resourceName);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            Properties properties = new Properties();
            InputStream stream = url.openStream();
            properties.load(stream);

            result.putAll((Map) properties);
        }
    }

    /**
     * Get MIME type from file name extension, if possible
     *
     * @param uri
     *            the string representing a file
     * @return the connected mime/type
     */
    public static String getMimeTypeForFile(String uri) {
        int dot = uri.lastIndexOf('.');
        String mime = null;
        if (dot >= 0) {
            mime = mimeTypes().get(uri.substring(dot + 1).toLowerCase());
        }
        return mime == null ? "application/octet-stream" : mime;
    }
}
