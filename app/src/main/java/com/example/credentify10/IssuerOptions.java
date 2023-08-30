package com.example.credentify10;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;



import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;
import java.util.Map;


public class IssuerOptions extends AppCompatActivity {

    private interface ConnectionIdCallback {
        void onConnectionIdReceived(String connectionId);
    }

    private ImageView qrCodeImageView;
    private TextView responseText, responseTextP;
    private Button recibirConexion, enviarConexion, crearConexion, manageButton;
    private String url_createInvitation, url_queryInvitation, connectionId;

    private SharedPreferences prefs;



    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_issuer);

        qrCodeImageView = findViewById(R.id.qr_code_image);

        recibirConexion = (Button) findViewById(R.id.buttonActionReceive);
        enviarConexion = (Button) findViewById(R.id.buttonActionSend);
        crearConexion = (Button) findViewById(R.id.buttonActionCreateInvitation);
        manageButton = findViewById(R.id.buttonManageCredentials);

        responseText = findViewById(R.id.responseText);
        responseTextP = findViewById(R.id.responseTextPost);

        url_createInvitation = getResources().getString(R.string.URL) + ":8001/connections/create-invitation";
        url_queryInvitation = getResources().getString(R.string.URL) + ":8001/connections/";

        // Inicializar SharedPreferences
        prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);

        recibirConexion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Realizar la solicitud GET con la URL actualizada
                makeRequest(url_queryInvitation);
            }
        });

        crearConexion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Start NewActivity.class

                String jsonBody = "";

                createInvitation(url_createInvitation, jsonBody, responseTextP, new ConnectionIdCallback() {
                    @Override
                    public void onConnectionIdReceived(String connectionId) {
                        // Obtener la URL del campo "invitation_url" del JSON de respuesta
                        try {
                            JSONObject jsonResponse = new JSONObject(responseTextP.getText().toString());
                            String invitationUrl = jsonResponse.getString("invitation_url");

                            // Generar el código QR a partir de la URL
                            generateQRCodeFromUrl(invitationUrl);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });

        enviarConexion.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent myIntent = new Intent(getApplicationContext(), CredentialsIssuer.class);
                startActivity(myIntent);
            }
        });

        manageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent myIntent = new Intent(getApplicationContext(), ManageIssuer.class);
                startActivity(myIntent);
            }
        });

    }

    private void generateQRCodeFromUrl(String url) {
        Bitmap qrCodeBitmap = generateQRCode(url);
        qrCodeImageView.setImageBitmap(qrCodeBitmap);
    }


    private Bitmap generateQRCode(String content) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8"); // Codificación de caracteres

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, 512, 512, hints);

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            return bitmap;
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void makeRequest(String url) {

        // Obtener el último valor de connectionId almacenado en SharedPreferences
        String connectionId = prefs.getString("connectionId", "");

        // Construir la URL con el último valor de connectionId
        String urlWithConnectionId = url + connectionId;

        // Crear instancia de OkHttpClient
        OkHttpClient client = new OkHttpClient();

        // Crear la solicitud GET
        Request request = new Request.Builder()
                .url(urlWithConnectionId)
                .build();

        // Realizar la solicitud de forma asíncrona
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Manejar el error de la solicitud
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // Obtener la respuesta exitosa
                final String responseBody = response.body().string();

                // Mostrar la respuesta en el TextView (requiere cambios en la interfaz de usuario desde el hilo principal)
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        responseText.setText(responseBody);
                    }
                });
            }
        });

    }


    private void createInvitation(String url, String jsonBody, TextView textID, ConnectionIdCallback callback) {
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

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                final String responseBody = response.body().string();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView textViewResponse = textID;
                        textViewResponse.setText(responseBody);

                        // Obtener el texto del TextView
                        String jsonResponseString = textViewResponse.getText().toString();

                        try {
                            // Crear un objeto JSONObject a partir de la cadena JSON
                            JSONObject jsonResponse = new JSONObject(jsonResponseString);

                            // Obtener el valor del campo "connection_id"
                            connectionId = jsonResponse.getString("connection_id");

                            // Guardar el valor en SharedPreferences
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("connectionId", connectionId);
                            editor.apply();

                            // Llamar al callback con el connectionId
                            callback.onConnectionIdReceived(connectionId);

                            // Obtener el valor del campo "invitation"
                            String invitation = jsonResponse.getString("invitation");

                            // Guardar solo el valor de "invitation"
                            textViewResponse.setText(invitation);

                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }



                    }
                });
            }
        });
    }


}
