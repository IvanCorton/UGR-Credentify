package com.example.credentify10;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CredentialHolder extends AppCompatActivity {

    private LinearLayout attributesForm;
    private EditText numAttributesEditText;
    private EditText schemaNameEditText;
    private Button addAttributesButton;
    private Button submitButton;
    private SharedPreferences sharedPreferences;
    private final Handler handler = new Handler();
    private Runnable checkSchemaNameRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.credentials_holder);

        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);

        attributesForm = findViewById(R.id.attributesForm2);
        numAttributesEditText = findViewById(R.id.numAttributesEditText2);
        schemaNameEditText = findViewById(R.id.schemaNameEditText2);
        addAttributesButton = findViewById(R.id.addAttributesButton2);
        submitButton = findViewById(R.id.submitButton2);

        addAttributesButton.setOnClickListener(v -> addAttributes());
        submitButton.setOnClickListener(v -> submitForm());

        setListeners();
    }

    private void setListeners() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                isFormValid();
            }
        };

        numAttributesEditText.addTextChangedListener(textWatcher);
        schemaNameEditText.addTextChangedListener(textWatcher);
    }

    private void addAttributes() {
        String numAttributesStr = numAttributesEditText.getText().toString().trim();
        if (!numAttributesStr.isEmpty()) {
            int numAttributes = Integer.parseInt(numAttributesStr);

            attributesForm.removeAllViews();
            for (int i = 0; i < numAttributes; i++) {
                LinearLayout attributeLayout = new LinearLayout(this);
                attributeLayout.setOrientation(LinearLayout.HORIZONTAL);
                attributeLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                EditText attributeNameEditText = new EditText(this);
                attributeNameEditText.setHint("Nombre del atributo " + (i + 1));
                attributeNameEditText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                EditText attributeValueEditText = new EditText(this);
                attributeValueEditText.setHint("Valor del atributo " + (i + 1));
                attributeValueEditText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                attributeLayout.addView(attributeNameEditText);
                attributeLayout.addView(attributeValueEditText);
                attributesForm.addView(attributeLayout);
            }

            isFormValid();
        }
    }

    private void submitForm() {
        if (isFormValid()) {
            String schemaName = schemaNameEditText.getText().toString().trim();
            JSONArray attributesArray = getAttributesArray();

            if(attributesArray.length() > 0 && !schemaName.isEmpty()){

                JSONObject credentialPreviewJson = new JSONObject();
                try {
                    credentialPreviewJson.put("@type", "issue-credential/1.0/credential-preview");
                    credentialPreviewJson.put("attributes", attributesArray);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                String connectionId = sharedPreferences.getString("connection_id_holder", "");
                String credentialDefinitionId = sharedPreferences.getString("credentialDefinitionId", "");

                performHttpGet(getResources().getString(R.string.URL) + ":8001/schemas/created", schemaResponse -> {
                    try {
                        JSONArray schemaIds = schemaResponse.getJSONArray("schema_ids");
                        if (schemaIds.length() > 0) {
                            String schemaId = schemaIds.getString(0);

                            JSONObject payloadJson = new JSONObject();
                            try {
                                payloadJson.put("auto_remove", true);
                                payloadJson.put("comment", "string");
                                payloadJson.put("connection_id", connectionId);
                                payloadJson.put("cred_def_id", credentialDefinitionId);
                                payloadJson.put("credential_proposal", credentialPreviewJson);
                                payloadJson.put("issuer_did", getIssuerDidFromCredentialDefinitionId(credentialDefinitionId));
                                payloadJson.put("schema_id", schemaId);
                                payloadJson.put("schema_issuer_did", getSchemaIssuerDidFromCredentialDefinitionId(credentialDefinitionId));
                                payloadJson.put("schema_name", schemaName);
                                payloadJson.put("schema_version", "0.1");
                                payloadJson.put("trace", false);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                            String url = getResources().getString(R.string.URL) + ":10001/issue-credential/send-proposal";
                            performHttpPost(url, payloadJson, response -> {
                                Toast.makeText(CredentialHolder.this, "Proposal sent successfully", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                });

                // Limpiar el formulario
                clearForm();
            }else {
                Toast.makeText(this, "Debe agregar al menos un atributo y un nombre de esquema", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private JSONArray getAttributesArray() {
        JSONArray attributesArray = new JSONArray();

        for (int i = 0; i < attributesForm.getChildCount(); i++) {
            View view = attributesForm.getChildAt(i);
            if (view instanceof LinearLayout) {
                LinearLayout attributeLayout = (LinearLayout) view;
                EditText attributeNameEditText = (EditText) attributeLayout.getChildAt(0);
                EditText attributeValueEditText = (EditText) attributeLayout.getChildAt(1);

                String attributeName = attributeNameEditText.getText().toString().trim();
                String attributeValue = attributeValueEditText.getText().toString().trim();

                if (!attributeName.isEmpty() && !attributeValue.isEmpty()) {
                    JSONObject attributeJson = new JSONObject();
                    try {
                        attributeJson.put("name", attributeName);
                        attributeJson.put("value", attributeValue);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    attributesArray.put(attributeJson);
                }
            }
        }

        return attributesArray;
    }

    private void performHttpPost(String url, JSONObject json, final Callback callback) {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                try {
                    JSONObject jsonObject = new JSONObject(responseBody);
                    handler.post(() -> callback.onResponse(jsonObject));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void startCheckingSchemaName(String schemaName) {
        handler.post(() -> {
            performHttpGet(getResources().getString(R.string.URL) + ":8001/credential-definitions/created", response -> {
                try {
                    JSONArray credentialDefinitionIds = response.getJSONArray("credential_definition_ids");

                    if (credentialDefinitionIds.length() > 0) {
                        String credentialDefinitionId = credentialDefinitionIds.getString(0);
                        String retrievedSchemaName = getSchemaNameFromCredentialDefinitionId(credentialDefinitionId);

                        if (retrievedSchemaName.equalsIgnoreCase(schemaName)) {
                            stopCheckingSchemaName();
                            handler.post(() -> {
                                Toast.makeText(CredentialHolder.this, "Schema name created successfully", Toast.LENGTH_SHORT).show();

                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putString("credentialDefinitionId", credentialDefinitionId);
                                editor.apply();

                            });
                        } else {
                            handler.postDelayed(checkSchemaNameRunnable, 2000);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        });
    }

    private void performHttpGet(String url, final Callback callback) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                handler.post(() -> Toast.makeText(CredentialHolder.this, "Request failed", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body().string();
                try {
                    JSONObject jsonObject = new JSONObject(responseBody);
                    handler.post(() -> callback.onResponse(jsonObject));
                } catch (JSONException e) {
                    e.printStackTrace();
                    handler.post(() -> Toast.makeText(CredentialHolder.this, "Invalid response", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private String getSchemaNameFromCredentialDefinitionId(String credentialDefinitionId) {
        // Parse the schema name from the credential definition ID
        String[] parts = credentialDefinitionId.split(":");
        return parts[parts.length - 1];
    }

    private String getIssuerDidFromCredentialDefinitionId(String credentialDefinitionId) {
        // Parse the issuer DID from the credential definition ID
        String[] parts = credentialDefinitionId.split(":");
        return parts[0];
    }

    private String getSchemaIssuerDidFromCredentialDefinitionId(String credentialDefinitionId) {
        // Parse the schema issuer DID from the credential definition ID
        String[] parts = credentialDefinitionId.split(":");
        return parts[0];
    }

    private void stopCheckingSchemaName() {
        if (checkSchemaNameRunnable != null) {
            handler.removeCallbacks(checkSchemaNameRunnable);
        }
    }

    private boolean isFormValid() {
        for (int i = 0; i < attributesForm.getChildCount(); i++) {
            View view = attributesForm.getChildAt(i);
            if (view instanceof LinearLayout) {
                LinearLayout attributeLayout = (LinearLayout) view;
                EditText attributeNameEditText = (EditText) attributeLayout.getChildAt(0);
                EditText attributeValueEditText = (EditText) attributeLayout.getChildAt(1);

                String attributeName = attributeNameEditText.getText().toString().trim();
                String attributeValue = attributeValueEditText.getText().toString().trim();

                if (attributeName.isEmpty() || attributeValue.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private interface Callback {
        void onResponse(JSONObject response);
    }


    private void clearForm() {
        // Limpiar el número de atributos
        numAttributesEditText.setText("");

        // Limpiar el nombre del esquema
        schemaNameEditText.setText("");

        // Limpiar los campos de atributos dinámicos
        attributesForm.removeAllViews();

        // Añadir un nuevo campo de atributo vacío
        addAttributes();

        // Restablecer la validación del formulario
        isFormValid();
    }

}
