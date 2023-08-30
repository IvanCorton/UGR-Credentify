package com.example.credentify10;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HolderOptions extends AppCompatActivity {

    private Timer timer;
    private boolean isConnected;
    private EditText invitationInfo;
    private Button botonAceptInvitation;
    private Button botonCheckConnect;
    private Button botonSendProp;
    private TextView invitationReply;
    private TextView connectionStatusTextView;
    private HolderAgentTask holderAgentTask;
    private Button buttonManageCredentials2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_holder);

        botonAceptInvitation = findViewById(R.id.botonAceptar);
        invitationInfo = findViewById(R.id.invitationText);
        invitationReply = findViewById(R.id.invitationResponse);
        botonCheckConnect = findViewById(R.id.checkConnectButton);
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView);
        botonSendProp = findViewById(R.id.buttonActionSend);
        buttonManageCredentials2 = findViewById(R.id.buttonManageCredentials2);

        timer = new Timer();
        isConnected = false;

        botonAceptInvitation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String info = invitationInfo.getText().toString();
                String url_receiveInvitation = getResources().getString(R.string.URL) + ":10001/connections/receive-invitation";

                createInvitation(url_receiveInvitation, info, invitationReply);
            }
        });

        botonCheckConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holderAgentTask == null) {
                    holderAgentTask = new HolderAgentTask(HolderOptions.this);
                    holderAgentTask.execute();

                    // Iniciar la consulta periódica del estado de conexión
                    startConnectionCheckTimer();
                }
            }
        });



        botonSendProp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(getApplicationContext(), CredentialHolder.class);
                startActivity(myIntent);
            }
        });

        buttonManageCredentials2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(getApplicationContext(), ManageHolder.class);
                startActivity(myIntent);
            }
        });

    }


    //1) Input New Invitation\n" "2) send cred proposal\n" "3) send request\n" "4) store credential

    // DEFINIR BIEN LOS NOMBRES Y LA MODULARIDAD ENTRE CLASES

    private void createInvitation(String url, String jsonBody, TextView textID) {
        MediaType mediaType = MediaType.parse("application/json");

        RequestBody body = RequestBody.create(jsonBody, mediaType);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        OkHttpClient client = new OkHttpClient();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                final String responseBody = response.body().string();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView textViewResponse = textID;
                        textViewResponse.setText(responseBody);

                        try {
                            // Obtener el valor de connection_id desde el JSON
                            JSONObject responseJson = new JSONObject(responseBody);
                            String connectionId = responseJson.getString("connection_id");

                            // Guardar el valor de connection_id en SharedPreferences
                            SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("connection_id_holder", connectionId);
                            editor.apply();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                e.printStackTrace();
                final String errorMessage = e.getMessage();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView textViewError = textID;
                        textViewError.setText("Request failed: " + errorMessage);
                    }
                });
            }
        });
    }

    public void setConnectionStatus(boolean isConnected) {
        this.isConnected = isConnected;
        updateConnectionStatusText();
    }

    private void updateConnectionStatusText() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectionStatusTextView.setTypeface(null, Typeface.BOLD);
                if (isConnected) {
                    connectionStatusTextView.setText("CONEXIÓN ESTABLECIDA");
                    connectionStatusTextView.setTextColor(ContextCompat.getColor(HolderOptions.this, R.color.green));
                } else {
                    connectionStatusTextView.setText("SIN CONEXIÓN");
                    connectionStatusTextView.setTextColor(ContextCompat.getColor(HolderOptions.this, R.color.red));
                }
            }
        });
    }

    private void startConnectionCheckTimer() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                if (holderAgentTask != null && holderAgentTask.getStatus() == AsyncTask.Status.FINISHED) {
                    holderAgentTask = new HolderAgentTask(HolderOptions.this);
                    holderAgentTask.execute();
                }
            }
        };

        // Consultar el estado de conexión cada 5 segundos (ajustar el intervalo según sea necesario)
        timer.schedule(timerTask, 0, 5000);
    }





}
