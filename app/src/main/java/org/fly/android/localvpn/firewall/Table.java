package org.fly.android.localvpn.firewall;

import org.apache.commons.codec.binary.StringUtils;
import org.fly.core.text.encrytor.Encryption;
import org.fly.core.text.json.Jsonable;
import org.fly.core.text.lp.Decryptor;
import org.fly.core.text.lp.EncryptedResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class Table {

    private Grid grid = null;
    private Decryptor decryptor;

    public Table() {

        Encryption.IBase64 base64 = new Base64();
        decryptor = new Decryptor(base64);
    }

    private synchronized void setGrid(Grid grid)
    {
        this.grid = grid;
    }

    public void tick()
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(!Thread.interrupted())
                {
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

                    String rsa = decryptor.getPublicKey();
                    System.out.println(rsa);
                    Request request = new Request.Builder()
                            .header("X-RSA", rsa)
                            .url("http://192.168.1.144/projects/protocol/api/v1/table")
                            .post(requestBody)
                            .build();

                    try {
                        Response response = client.newCall(request).execute();

                        if (response.code() == 200)
                        {
                            ResponseBody body = response.body();
                            String string = body.string();
                            body.close();
                            response.close();

                            EncryptedResult result = Jsonable.fromJson(EncryptedResult.class, string);

                            String data = decryptor.decode(result.encrypted, result.data);

                            setGrid(Jsonable.fromJson(Grid.class, data));
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }

                    try {

                        Thread.sleep(3000 + new Random().nextInt(10000));
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

        public static class Dns {
            public List<String> A = new ArrayList<>();
            public List<String> AAAA = new ArrayList<>();
            public List<String> CNAME = new ArrayList<>();
        }

        public static class Http {
            public String POST = null;
            public String GET = null;
            public String PUT = null;
            public String DELETE = null;
        }
    }


}
