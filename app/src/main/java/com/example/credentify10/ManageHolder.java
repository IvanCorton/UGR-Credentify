package com.example.credentify10;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ManageHolder extends AppCompatActivity {

    private RecyclerView credentialsRecyclerView;
    private CredentialsAdapter credentialsAdapter;
    private List<Credential> credentialList;

    private Handler handler;
    private Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manage_holder);

        credentialsRecyclerView = findViewById(R.id.credentialsRecyclerView);
        credentialsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        credentialList = new ArrayList<>();
        credentialsAdapter = new CredentialsAdapter(credentialList);
        credentialsRecyclerView.setAdapter(credentialsAdapter);

        // Inicializar el Handler y el Runnable
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                // Realizar la petición GET al endpoint para obtener las propuestas de credenciales
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(getResources().getString(R.string.URL) + ":10001/issue-credential/records")
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e("API Request", "Error al realizar la petición GET", e);
                        // Volver a ejecutar el Runnable después de 5 segundos
                        handler.postDelayed(runnable, 5000);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            final String responseData = response.body().string();
                            runOnUiThread(() -> processResponseData(responseData));
                        } else {
                            Log.e("API Request", "Error en la respuesta: " + response.code());
                        }

                        // Volver a ejecutar el Runnable después de 5 segundos
                        handler.postDelayed(runnable, 5000);
                    }
                });
            }
        };

        // Ejecutar el Runnable por primera vez
        handler.post(runnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Detener la ejecución del Runnable al destruir la actividad
        handler.removeCallbacks(runnable);
    }


    private void processResponseData(String responseData) {
        try {
            JSONObject responseJson = new JSONObject(responseData);
            JSONArray resultsArray = responseJson.getJSONArray("results");

            for (int i = 0; i < resultsArray.length(); i++) {
                JSONObject credentialJson = resultsArray.getJSONObject(i);
                String state = credentialJson.getString("state");
                String credentialExchangeId = credentialJson.getString("credential_exchange_id");

                // Buscar si el credentialExchangeId ya existe en la lista actual
                Credential existingCredential = null;
                for (Credential credential : credentialList) {
                    if (credential.getCredentialExchangeId().equals(credentialExchangeId)) {
                        existingCredential = credential;
                        break;
                    }
                }

                if (existingCredential != null) {
                    // Actualizar el estado del credencial existente
                    existingCredential.setState(state);
                } else {
                    // Agregar un nuevo credencial a la lista si no existe
                    Credential newCredential = new Credential();
                    newCredential.setState(state);

                    if (credentialJson.has("credential_proposal_dict")) {
                        JSONObject proposalDictJson = credentialJson.getJSONObject("credential_proposal_dict");
                        if (proposalDictJson.has("credential_proposal")) {
                            JSONObject credentialProposalJson = proposalDictJson.getJSONObject("credential_proposal");
                            if (credentialProposalJson.has("attributes")) {
                                JSONArray attributesArray = credentialProposalJson.getJSONArray("attributes");
                                StringBuilder attributesBuilder = new StringBuilder();
                                for (int j = 0; j < attributesArray.length(); j++) {
                                    JSONObject attributeJson = attributesArray.getJSONObject(j);
                                    String name = attributeJson.getString("name");
                                    String value = attributeJson.getString("value");
                                    attributesBuilder.append(name).append(": ").append(value);
                                    if (j < attributesArray.length() - 1) {
                                        attributesBuilder.append("\n");
                                    }
                                }
                                newCredential.setAttributes(attributesBuilder.toString());
                            }
                        }
                    }

                    newCredential.setCredentialExchangeId(credentialExchangeId);

                    // Agregar el nuevo credencial a la lista actual
                    credentialList.add(newCredential);
                }
            }

            // Notificar al adaptador que los datos han cambiado
            credentialsAdapter.notifyDataSetChanged();

        } catch (JSONException e) {
            Log.e("JSON Parsing", "Error al analizar la respuesta JSON", e);
        }
    }




    private class CredentialsAdapter extends RecyclerView.Adapter<CredentialsAdapter.CredentialViewHolder> {

        private List<Credential> credentialList;

        public CredentialsAdapter(List<Credential> credentialList) {
            this.credentialList = credentialList;
        }

        @NonNull
        @Override
        public CredentialViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_propuesta_credencial, parent, false);
            return new CredentialViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CredentialViewHolder holder, int position) {
            Credential credential = credentialList.get(position);

            holder.tituloTextView.setText("Credencial #" + (position + 1));
            holder.descripcionTextView.setText(credential.getState() + "\n" + credential.getAttributes());

            if (credential.getState().equals("offer_received")) {
                holder.aceptarButton.setVisibility(View.VISIBLE);
            } else {
                holder.aceptarButton.setVisibility(View.GONE);
            }

            holder.aceptarButton.setOnClickListener(v -> {
                // Realizar la petición POST al endpoint para aceptar la propuesta de credencial
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(getResources().getString(R.string.URL) + ":10001/issue-credential/records/" + credential.getCredentialExchangeId() + "/send-request")
                        .post(new FormBody.Builder().build())
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e("API Request", "Error al realizar la petición POST", e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Propuesta de credencial aceptada", Toast.LENGTH_SHORT).show());
                        } else {
                            Log.e("API Request", "Error en la respuesta: " + response.code());
                        }
                    }
                });
            });

            holder.eliminarButton.setOnClickListener(v -> {
                // Realizar la petición DELETE al endpoint para eliminar la propuesta de credencial
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(getResources().getString(R.string.URL) + ":10001/issue-credential/records/" + credential.getCredentialExchangeId())
                        .delete()
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e("API Request", "Error al realizar la petición DELETE", e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            runOnUiThread(() -> {
                                Toast.makeText(getApplicationContext(), "Propuesta de credencial eliminada", Toast.LENGTH_SHORT).show();
                                credentialList.remove(credential);
                                notifyDataSetChanged();
                            });
                        } else {
                            Log.e("API Request", "Error en la respuesta: " + response.code());
                        }
                    }
                });
            });
        }

        @Override
        public int getItemCount() {
            return credentialList.size();
        }

        public class CredentialViewHolder extends RecyclerView.ViewHolder {

            TextView tituloTextView;
            TextView descripcionTextView;
            Button aceptarButton;
            Button eliminarButton;

            public CredentialViewHolder(@NonNull View itemView) {
                super(itemView);
                tituloTextView = itemView.findViewById(R.id.tituloTextView);
                descripcionTextView = itemView.findViewById(R.id.descripcionTextView);
                aceptarButton = itemView.findViewById(R.id.aceptarButton);
                eliminarButton = itemView.findViewById(R.id.eliminarButton);
            }
        }
    }

    private class Credential {
        private String state;
        private String attributes;
        private String credentialExchangeId;

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getAttributes() {
            return attributes;
        }

        public void setAttributes(String attributes) {
            this.attributes = attributes;
        }

        public String getCredentialExchangeId() {
            return credentialExchangeId;
        }

        public void setCredentialExchangeId(String credentialExchangeId) {
            this.credentialExchangeId = credentialExchangeId;
        }
    }
}
