/*
** Copyright 2015, Mohamed Naufal
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package org.fly.android.localvpn;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.concurrent.TimeUnit;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;


public class LocalVPN extends Activity
{
    private static final String TAG = LocalVPN.class.getSimpleName();

    private static final int VPN_REQUEST_CODE = 0x0F;

    private boolean waitingForVPNStart;

    private BroadcastReceiver vpnStateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (LocalVPNService.BROADCAST_VPN_STATE.equals(intent.getAction()))
            {
                if (intent.getBooleanExtra("running", false))
                    waitingForVPNStart = false;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_vpn);
        final Button vpnButton = findViewById(R.id.vpn);
        vpnButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startVPN();
            }
        });
        final Button httpButton = findViewById(R.id.okhttp);
        httpButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        OkHttpClient client = new OkHttpClient.Builder()
                                .connectTimeout(1000, TimeUnit.SECONDS)
                                .readTimeout(1000, TimeUnit.SECONDS)
                                .writeTimeout(1000, TimeUnit.SECONDS)
                                .retryOnConnectionFailure(false)
                                .build();
                        StringBuilder big = new StringBuilder();
                        for(int i = 0; i < 100; i++)
                            big.append("abcdefghijklmnopqrstuvwxyz0123456789");


                        RequestBody requestBody = new MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("abc", "2")
                                .addFormDataPart("abc1", "2")
                                .addFormDataPart("post", big.toString())
                                .build();

                        /*requestBody = new FormBody.Builder()
                                .add("post", big.toString())
                                .add("abc", "2")
                                .add("abc1", "2")
                                .build();*/

                        Request request = new Request.Builder()
                                .url("http://192.168.1.144/1.php?get=" + big.toString())
                                .post(requestBody)
                                .build();

                        try {
                            Response response = client.newCall(request).execute();
                            ResponseBody body = response.body();
                            System.out.println("Response:------------"+response.code()+"---------------------------");
                            System.out.println(body.string());
                            System.out.println("---------------------------------------");

                            body.close();
                            response.close();
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        });

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Log.e(TAG, e.getMessage(), e);

            }
        });

        waitingForVPNStart = false;
        LocalBroadcastManager.getInstance(this).registerReceiver(vpnStateReceiver,
                new IntentFilter(LocalVPNService.BROADCAST_VPN_STATE));
    }

    private void startVPN()
    {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null)
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        else
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK)
        {
            waitingForVPNStart = true;
            startService(new Intent(this, LocalVPNService.class));
            enableButton(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        enableButton(!waitingForVPNStart && !LocalVPNService.isRunning());
    }

    private void enableButton(boolean enable)
    {
        final Button vpnButton = findViewById(R.id.vpn);
        if (enable)
        {
            vpnButton.setEnabled(true);
            vpnButton.setText(R.string.start_vpn);
        }
        else
        {
            vpnButton.setEnabled(false);
            vpnButton.setText(R.string.stop_vpn);
        }
    }
}
