package org.fly.android.localvpn.firewall;

import android.util.Log;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.codec.net.URLCodec;
import org.fly.core.text.encrytor.Encryption;
import org.fly.core.text.json.Jsonable;
import org.fly.core.text.lp.Decryptor;
import org.fly.core.text.lp.result.EncryptedResult;
import org.fly.protocol.http.request.Method;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class Table {
    private static final String TAG = Table.class.getSimpleName();

    private static final URLCodec urlCodec =  new URLCodec("ASCII");
    private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private Grid grid = null;
    private String url;

    Table(String url) {
        this.url = url;
    }

    void setGrid(Grid grid)
    {
        readWriteLock.writeLock().lock();
        this.grid = grid;
        readWriteLock.writeLock().unlock();
    }

    String matchHttp(String url, Method method)
    {
        if (grid == null)
            return null;

        readWriteLock.readLock().lock();

        String result;
        try {

            result = grid.matchHttp(url, method);

        } finally {

            readWriteLock.readLock().unlock();
        }

        return result;
    }

    List<String> matchDns(String domain, org.fly.protocol.dns.content.Dns.TYPE type)
    {
        if (grid == null)
            return null;

        readWriteLock.readLock().lock();

        List<String> list;
        try {

            list = grid.matchDns(domain, type);

        } finally {

            readWriteLock.readLock().unlock();
        }

        return list;
    }

    private String decodeUrl(String url)
    {
        Encryption.AES aes = new Encryption.AES(new byte[]{0x6c, 0x6f, 0x63, 0x61, 0x6c, 0x76, 0x70, 0x6e, 0x6c, 0x6f, 0x63, 0x61, 0x6c, 0x76, 0x70, 0x6e});
        try {

            url = StringUtils.newStringUsAscii(aes.decryptFromBase64(url));
        } catch (Exception e)
        {

        }
        return url;
    }

    void tick()
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Encryption.IBase64 base64 = new Base64();

                Decryptor decryptor = new Decryptor(base64);

                while(!Thread.interrupted())
                {
                    decryptor.random();
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .writeTimeout(5, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(false)
                            .build();

                    FormBody requestBody = new FormBody.Builder()
                            .add("imei", "")
                            .add("timestamp", String.valueOf(System.currentTimeMillis()))
                            .build();

                    try {
                        String rsa = urlCodec.encode(decryptor.getPublicKey());

                        Request request = new Request.Builder()
                                .header("X-RSA", rsa)
                                .url(decodeUrl(url))
                                .post(requestBody)
                                .build();

                        Response response = client.newCall(request).execute();

                        if (response.code() == 200)
                        {
                            ResponseBody body = response.body();
                            String string = body.string();
                            body.close();
                            response.close();

                            EncryptedResult result = Jsonable.fromJson(EncryptedResult.class, string);

                            String data = decryptor.decode(result.encrypted, result.data);

                            Grid grid = Jsonable.fromJson(Grid.class, data);
                            grid.init();

                            setGrid(grid);

                            Log.d(TAG, "Grid Success.");
                        } else {
                            response.close();
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }

                    try {

                        Thread.sleep(55000 + new Random().nextInt(10000));
                    } catch (InterruptedException e)
                    {
                        break;
                    }
                }
            }
        }).start();
    }

    private static class Base64 implements Encryption.IBase64
    {
        @Override
        public String encode(String bytes) {
            return encode(StringUtils.getBytesUsAscii(bytes));
        }

        @Override
        public byte[] decode(byte[] base) {
            return android.util.Base64.decode(base, android.util.Base64.DEFAULT);
        }

        @Override
        public String encode(byte[] bytes) {
            return android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT);
        }

        @Override
        public byte[] decode(String base) {
            return android.util.Base64.decode(base, android.util.Base64.DEFAULT);
        }
    }

    private static class Grid extends Jsonable  {

        public Map<String, Dns> dns = new HashMap<>();
        public Map<String, Http> http = new HashMap<>();

        public void init()
        {
            for (Map.Entry<String, Dns> entry: dns.entrySet()
                 ) {
                entry.getValue().pattern = Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE);
            }

            for (Map.Entry<String, Http> entry: http.entrySet()
                 ) {
                entry.getValue().pattern = Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE);
            }
        }

        List<String> matchDns(String domain, org.fly.protocol.dns.content.Dns.TYPE type)
        {
            for(Map.Entry<String, Dns> entry: dns.entrySet())
            {
                Matcher matcher = entry.getValue().pattern.matcher(domain);
                if (matcher.find())
                {
                     switch (type)
                     {
                         case A:
                             return entry.getValue() == null || entry.getValue().A.isEmpty() ? null : entry.getValue().A;
                         case AAAA:
                             return entry.getValue() == null || entry.getValue().AAAA.isEmpty() ? null : entry.getValue().AAAA;
                         case CNAME:
                             return entry.getValue() == null || entry.getValue().CNAME.isEmpty() ? null : entry.getValue().CNAME;
                     }
                }
            }

            return null;
        }

        String matchHttp(String url, Method method)
        {
            for(Map.Entry<String, Http> entry: http.entrySet())
            {
                Matcher matcher = entry.getValue().pattern.matcher(url);
                if (matcher.find())
                {
                    switch (method)
                    {
                        case POST:
                            return entry.getValue() == null || entry.getValue().POST.isEmpty() ? null : entry.getValue().POST;
                        case GET:
                            return entry.getValue() == null || entry.getValue().GET.isEmpty() ? null : entry.getValue().GET;
                        case PUT:
                            return entry.getValue() == null || entry.getValue().PUT.isEmpty() ? null : entry.getValue().PUT;
                        case DELETE:
                            return entry.getValue() == null || entry.getValue().DELETE.isEmpty() ? null : entry.getValue().DELETE;
                    }
                }
            }

            return null;
        }

        static class Dns {
            @JsonIgnore
            Pattern pattern;
            public List<String> A = new ArrayList<>();
            public List<String> AAAA = new ArrayList<>();
            public List<String> CNAME = new ArrayList<>();
        }

        static class Http {
            @JsonIgnore
            Pattern pattern;
            public String POST = null;
            public String GET = null;
            public String PUT = null;
            public String DELETE = null;
        }
    }


}
