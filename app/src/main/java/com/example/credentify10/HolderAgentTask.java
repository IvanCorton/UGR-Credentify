package com.example.credentify10;

import android.os.AsyncTask;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HolderAgentTask extends AsyncTask<Void, Void, List<String>> {

    private static final String HOLDER_ENDPOINT = "http://192.168.1.129:10001/connections";
    private final AppCompatActivity activity;
    private List<String> previousConnectionIds;

    public HolderAgentTask(AppCompatActivity activity) {
        this.activity = activity;
        previousConnectionIds = new ArrayList<>();
    }

    @Override
    protected List<String> doInBackground(Void... voids) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(HOLDER_ENDPOINT)
                .build();

        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                String responseData = response.body().string();
                return parseConnectionIds(responseData);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    protected void onPostExecute(List<String> connectionIds) {
        if (connectionIds != null) {
            boolean isConnected = !connectionIds.isEmpty();
            ((HolderOptions) activity).setConnectionStatus(isConnected);

            // Verificar si se establecieron nuevas conexiones
            if (!previousConnectionIds.equals(connectionIds)) {
                previousConnectionIds = connectionIds;
            }
        }
    }

    private List<String> parseConnectionIds(String responseData) {
        List<String> connectionIds = new ArrayList<>();
        try {
            JSONObject jsonResponse = new JSONObject(responseData);
            JSONArray results = jsonResponse.getJSONArray("results");
            for (int i = 0; i < results.length(); i++) {
                JSONObject connection = results.getJSONObject(i);
                String connectionId = connection.getString("connection_id");
                String rfc23State = connection.getString("rfc23_state");
                if ("completed".equals(rfc23State)) {
                    connectionIds.add(connectionId);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return connectionIds;
    }
}
