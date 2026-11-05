package hu.zsirkreta.app.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Kis HTTP-kliens (HttpURLConnection) a KRÉTA token- és adatvégpontjaihoz. */
public final class Http {
    private Http() {}

    public static final class Resp {
        public int status;
        public String body = "", url = "", location;
        public boolean ok() { return status >= 200 && status < 300; }
    }

    public static String form(Map<String, String> fields) {
        StringBuilder b = new StringBuilder();
        try {
            for (Map.Entry<String, String> e : fields.entrySet()) {
                if (b.length() > 0) b.append('&');
                b.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
            }
        } catch (java.io.UnsupportedEncodingException ignored) {}
        return b.toString();
    }

    public static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (java.io.UnsupportedEncodingException e) { return s; }
    }

    /** Egy kérés, átirányítás követése nélkül. form != null esetén application/x-www-form-urlencoded törzzsel. */
    public static Resp send(String method, String url, Map<String, String> headers, String form, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestMethod(method);
        if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
        try {
            if (form != null) {
                byte[] body = form.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                c.setFixedLengthStreamingMode(body.length);
                try (OutputStream os = c.getOutputStream()) { os.write(body); }
            }
            Resp r = new Resp();
            r.status = c.getResponseCode();
            r.url = url;
            r.location = c.getHeaderField("Location");
            InputStream is = r.status >= 400 ? c.getErrorStream() : c.getInputStream();
            r.body = is == null ? "" : readAll(is);
            return r;
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream is) throws IOException {
        try (InputStream in = is; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 20_000_000) throw new IOException("túl nagy válasz");
            }
            return out.toString("UTF-8");
        }
    }
}
