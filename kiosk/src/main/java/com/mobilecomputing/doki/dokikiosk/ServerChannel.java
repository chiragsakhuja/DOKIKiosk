package com.mobilecomputing.doki.dokikiosk;

import android.app.ActivityManager;

import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class ServerChannel {
    private URL url;

    public ServerChannel(final String urlStr) {
        try {
            url = new URL(urlStr);
        } catch(MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public void postData(final JSONObject data) {
        Thread post = new Thread(new Runnable() {
            public void run() {
            try {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST"); // hear you are telling that it is a POST request, which can be changed into "PUT", "GET", "DELETE" etc.
                connection.setRequestProperty("Content-Type", "application/json"); // here you are setting the `Content-Type` for the data you are sending which is `application/json`
                connection.connect();

                DataOutputStream output = new DataOutputStream(connection.getOutputStream());
                output.writeBytes(data.toString());
                output.flush();

                int response = connection.getResponseCode();
                if (response < 200 || response > 399) {
                    throw new Exception("Could not POST");
                }

                output.close();
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
            }
        });

        post.start();
        try {
            post.join();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
    }
}
