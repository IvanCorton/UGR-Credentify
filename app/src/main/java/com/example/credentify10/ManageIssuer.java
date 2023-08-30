package com.example.credentify10;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ManageIssuer extends AppCompatActivity {

    private RecyclerView credentialsRecyclerView;
    private CredentialsAdapter credentialsAdapter;
    private List<PropuestaCredencial> propuestasCredenciales;

    private Handler handler;
    private Runnable refreshRunnable;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manage_issuer);

        credentialsRecyclerView = findViewById(R.id.credentialsRecyclerView);
        credentialsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        propuestasCredenciales = new ArrayList<>();
        credentialsAdapter = new CredentialsAdapter(propuestasCredenciales);
        credentialsRecyclerView.setAdapter(credentialsAdapter);

        // Inicializar SharedPreferences
        prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);

        // Inicializar el Handler y el Runnable para la actualización periódica
        handler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                // Llamar al método para obtener las propuestas de credenciales desde el endpoint JSON
                obtenerPropuestasCredenciales();

                // Programar la próxima actualización después de 5 segundos
                handler.postDelayed(this, 5000);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Iniciar la actualización periódica cuando la actividad está en primer plano
        handler.postDelayed(refreshRunnable, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Detener la actualización periódica cuando la actividad está en segundo plano
        handler.removeCallbacks(refreshRunnable);
    }



    private void enviarSolicitudEmisionCredencial(String credentialExchangeId, String comment) {
        OkHttpClient client = new OkHttpClient();

        // Construir el objeto JSON para el payload
        JsonObject payloadJson = new JsonObject();
        payloadJson.addProperty("comment", comment);

        // Construir la solicitud POST con el objeto JSON como cuerpo/payload
        String url = getResources().getString(R.string.URL) + ":8001/issue-credential/records/" + credentialExchangeId + "/issue";
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), payloadJson.toString()))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // La solicitud se realizó con éxito
                    // Realizar cualquier acción adicional requerida
                } else {
                    // La solicitud no se completó con éxito
                    // Manejar el error apropiadamente
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                // Ocurrió un error al realizar la solicitud
                // Manejar el error apropiadamente
            }
        });
    }



    private void obtenerPropuestasCredenciales() {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(getResources().getString(R.string.URL) + ":8001/issue-credential/records")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    ResponseBody responseBody = response.body();
                    if (responseBody != null) {
                        try {
                            String jsonData = responseBody.string();

                            // Parsear el JSON y obtener las propuestas de credenciales
                            Gson gson = new Gson();
                            JsonElement jsonElement = JsonParser.parseString(jsonData);
                            JsonObject jsonObject = jsonElement.getAsJsonObject();
                            JsonArray resultsArray = jsonObject.getAsJsonArray("results");

                            propuestasCredenciales.clear();
                            for (int i = 0; i < resultsArray.size(); i++) {
                                JsonObject resultObject = resultsArray.get(i).getAsJsonObject();
                                JsonObject credentialProposalDict = resultObject.getAsJsonObject("credential_proposal_dict");
                                JsonObject credentialProposal = credentialProposalDict.getAsJsonObject("credential_proposal");
                                JsonArray attributesArray = credentialProposal.getAsJsonArray("attributes");

                                String schemaName = credentialProposalDict.get("schema_name").getAsString();
                                String state = resultObject.get("state").getAsString();
                                String credentialExchangeId = resultObject.get("credential_exchange_id").getAsString();

                                StringBuilder attributesBuilder = new StringBuilder();
                                for (int j = 0; j < attributesArray.size(); j++) {
                                    JsonObject attribute = attributesArray.get(j).getAsJsonObject();
                                    String attributeName = attribute.get("name").getAsString();
                                    String attributeValue = attribute.get("value").getAsString();
                                    attributesBuilder.append(attributeName).append(": ").append(attributeValue).append("\n");
                                }

                                String titulo = "Solicitud de credencial " + (i + 1);
                                String descripcion = "Schema Name: " + schemaName + "\nState: " + state + "\n\n" + attributesBuilder.toString();
                                propuestasCredenciales.add(new PropuestaCredencial(titulo, descripcion, credentialExchangeId, state));
                            }

                            // Actualizar la UI en el hilo principal
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // Notificar al adaptador que los datos han cambiado
                                    credentialsAdapter.notifyDataSetChanged();
                                }
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }
        });
    }



    private class CredentialsAdapter extends RecyclerView.Adapter<CredentialsAdapter.ViewHolder> {

        private List<PropuestaCredencial> propuestasCredenciales;

        public CredentialsAdapter(List<PropuestaCredencial> propuestasCredenciales) {
            this.propuestasCredenciales = propuestasCredenciales;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_propuesta_credencial, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PropuestaCredencial propuestaCredencial = propuestasCredenciales.get(position);
            holder.tituloTextView.setText(propuestaCredencial.getTitulo());
            holder.descripcionTextView.setText(propuestaCredencial.getDescripcion());

            String state = propuestaCredencial.getState();

            if (state.equals("offer_sent") || state.equals("credential_issued")) {
                holder.aceptarButton.setVisibility(View.GONE);
            } else {
                holder.aceptarButton.setVisibility(View.VISIBLE);
            }

            holder.aceptarButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Acción cuando se hace clic en el botón Aceptar
                    String credentialExchangeId = propuestaCredencial.getCredentialExchangeId();

                    // Verificar si el estado es "request_received"
                    if (state.equals("request_received")) {
                        String comment = "Aceptando la propuesta de credencial"; // Cambia esto según tus necesidades
                        // Llamar a la función para enviar la solicitud de emisión de la credencial con el comentario
                        enviarSolicitudEmisionCredencial(credentialExchangeId, comment);

                    } else {
                        // Llamar a la función para enviar la aceptación de la credencial
                        enviarAceptacionCredencial(credentialExchangeId);
                    }
                }
            });

            holder.eliminarButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String credentialExchangeId = propuestaCredencial.getCredentialExchangeId();
                    eliminarPropuestaCredencial(credentialExchangeId);
                }
            });
        }

        @Override
        public int getItemCount() {
            return propuestasCredenciales.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            public TextView tituloTextView;
            public TextView descripcionTextView;
            public Button aceptarButton;
            public Button eliminarButton;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tituloTextView = itemView.findViewById(R.id.tituloTextView);
                descripcionTextView = itemView.findViewById(R.id.descripcionTextView);
                aceptarButton = itemView.findViewById(R.id.aceptarButton);
                eliminarButton = itemView.findViewById(R.id.eliminarButton);
            }
        }
    }

    private static class PropuestaCredencial {
        private final String titulo;
        private final String descripcion;
        private final String credentialExchangeId;
        private final String state;

        public PropuestaCredencial(String titulo, String descripcion, String credentialExchangeId, String state) {
            this.titulo = titulo;
            this.descripcion = descripcion;
            this.credentialExchangeId = credentialExchangeId;
            this.state = state;
        }

        public String getTitulo() {
            return titulo;
        }

        public String getDescripcion() {
            return descripcion;
        }

        public String getCredentialExchangeId() {
            return credentialExchangeId;
        }

        public String getState() { return state; }
    }



    private void eliminarPropuestaCredencial(String credentialExchangeId) {
        OkHttpClient client = new OkHttpClient();

        String url = getResources().getString(R.string.URL) + ":8001/issue-credential/records/" + credentialExchangeId;

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                if (response.isSuccessful()) {
                    // Eliminación exitosa, puedes realizar acciones adicionales si es necesario
                } else {
                    // Error al eliminar, maneja el caso según tus necesidades
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void enviarAceptacionCredencial(String credentialExchangeId) {
        OkHttpClient client = new OkHttpClient();

        // Construir la solicitud POST sin cuerpo/payload
        String url = getResources().getString(R.string.URL) + ":8001/issue-credential/records/" + credentialExchangeId + "/send-offer";
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(null, new byte[0]))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // La solicitud se realizó con éxito
                    // Realizar cualquier acción adicional requerida
                } else {
                    // La solicitud no se completó con éxito
                    // Manejar el error apropiadamente
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                // Ocurrió un error al realizar la solicitud
                // Manejar el error apropiadamente
            }
        });
    }



}
