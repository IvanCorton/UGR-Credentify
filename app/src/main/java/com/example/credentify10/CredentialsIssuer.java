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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CredentialsIssuer extends AppCompatActivity {

    private LinearLayout attributesForm;
    private EditText numAttributesEditText;
    private EditText schemaNameEditText;
    private Button addAttributesButton;
    private Button submitButton;
    private TextView credentialDefinitionIdTextView;
    private SharedPreferences sharedPreferences;
    private Handler handler = new Handler();
    private Runnable checkSchemaNameRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.credentials_issuer);

        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);

        attributesForm = findViewById(R.id.attributesForm);
        numAttributesEditText = findViewById(R.id.numAttributesEditText);
        schemaNameEditText = findViewById(R.id.schemaNameEditText);
        addAttributesButton = findViewById(R.id.addAttributesButton);
        submitButton = findViewById(R.id.submitButton);
        credentialDefinitionIdTextView = findViewById(R.id.credentialDefinitionIdTextView);

        setListeners();
    }

    private void setListeners() {
        addAttributesButton.setOnClickListener(v -> {
            String numAttributesStr = numAttributesEditText.getText().toString().trim();
            if (!numAttributesStr.isEmpty()) {
                int numAttributes = Integer.parseInt(numAttributesStr);

                attributesForm.removeAllViews();
                for (int i = 0; i < numAttributes; i++) {
                    EditText editText = new EditText(CredentialsIssuer.this);
                    editText.setHint("Atributo " + (i + 1));
                    attributesForm.addView(editText);
                }

                isFormValid();
            }
        });

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

        submitButton.setOnClickListener(v -> {
            if (isFormValid()) {
                int numAttributes = Integer.parseInt(numAttributesEditText.getText().toString().trim());
                String schemaName = schemaNameEditText.getText().toString().trim();
                JSONArray attributesArray = getAttributesArray();

                startCheckingSchemaName(schemaName);

                JSONObject schemaJson = new JSONObject();
                try {
                    schemaJson.put("attributes", attributesArray);
                    schemaJson.put("schema_name", schemaName);
                    schemaJson.put("schema_version", "0.1");
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                performHttpPost(getResources().getString(R.string.URL) + ":8001/schemas", schemaJson, response -> {
                    try {
                        String schemaId = response.getString("schema_id");

                        JSONObject credentialDefinitionJson = new JSONObject();
                        credentialDefinitionJson.put("revocation_registry_size", 1000);
                        credentialDefinitionJson.put("schema_id", schemaId);
                        credentialDefinitionJson.put("support_revocation", true);
                        credentialDefinitionJson.put("tag", schemaName);

                        performHttpPost(getResources().getString(R.string.URL) + ":8001/credential-definitions", credentialDefinitionJson, response2 -> {
                            try {
                                String credentialDefinitionId = response2.getString("credential_definition_id");

                                stopCheckingSchemaName();

                                credentialDefinitionIdTextView.setText("Credential Definition ID: " + credentialDefinitionId);

                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putString("credentialDefinitionId", credentialDefinitionId);
                                editor.apply();

                                Toast.makeText(CredentialsIssuer.this, "Credential Definition created successfully", Toast.LENGTH_SHORT).show();
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        });
    }

    private JSONArray getAttributesArray() {
        JSONArray attributesArray = new JSONArray();

        for (int i = 0; i < attributesForm.getChildCount(); i++) {
            View view = attributesForm.getChildAt(i);
            if (view instanceof EditText) {
                EditText editText = (EditText) view;
                String attributeValue = editText.getText().toString().trim();
                if (!attributeValue.isEmpty()) {
                    attributesArray.put(attributeValue);
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
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                handler.post(() -> Toast.makeText(CredentialsIssuer.this, "Request failed", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body().string();
                try {
                    JSONObject jsonObject = new JSONObject(responseBody);
                    handler.post(() -> callback.onResponse(jsonObject));
                } catch (JSONException e) {
                    e.printStackTrace();
                    handler.post(() -> Toast.makeText(CredentialsIssuer.this, "Invalid response", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void startCheckingSchemaName(String schemaName) {
        checkSchemaNameRunnable = () -> {
            performHttpGet(getResources().getString(R.string.URL) + ":8001/credential-definitions/created", response -> {
                try {
                    JSONArray credentialDefinitionIds = response.getJSONArray("credential_definition_ids");

                    for (int i = 0; i < credentialDefinitionIds.length(); i++) {
                        String credentialDefinitionId = credentialDefinitionIds.getString(i);
                        String retrievedSchemaName = getSchemaNameFromCredentialDefinitionId(credentialDefinitionId);

                        if (retrievedSchemaName.equalsIgnoreCase(schemaName)) {
                            handler.post(() -> {
                                stopCheckingSchemaName();
                                Toast.makeText(CredentialsIssuer.this, "Schema name already exists", Toast.LENGTH_SHORT).show();

                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putString("credentialDefinitionId", credentialDefinitionId);
                                editor.apply();

                                credentialDefinitionIdTextView.setText("Credential Definition ID: " + credentialDefinitionId); // Set the credential definition ID as text
                            });
                            break;
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });

            handler.postDelayed(checkSchemaNameRunnable, 2000);
        };

        handler.post(checkSchemaNameRunnable);
    }

    private String getSchemaNameFromCredentialDefinitionId(String credentialDefinitionId) {
        // Parse the schema name from the credential definition ID
        String[] parts = credentialDefinitionId.split(":");
        return parts[parts.length - 1];
    }

    private void stopCheckingSchemaName() {
        if (checkSchemaNameRunnable != null) {
            handler.removeCallbacks(checkSchemaNameRunnable);
        }
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
                handler.post(() -> Toast.makeText(CredentialsIssuer.this, "Request failed", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseBody = response.body().string();
                try {
                    JSONObject jsonObject = new JSONObject(responseBody);
                    handler.post(() -> callback.onResponse(jsonObject));
                } catch (JSONException e) {
                    e.printStackTrace();
                    handler.post(() -> Toast.makeText(CredentialsIssuer.this, "Invalid response", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
    private boolean isFormValid() {
        for (int i = 0; i < attributesForm.getChildCount(); i++) {
            View view = attributesForm.getChildAt(i);
            if (view instanceof EditText) {
                EditText editText = (EditText) view;
                String attributeValue = editText.getText().toString().trim();
                if (attributeValue.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private interface Callback {
        void onResponse(JSONObject response);
    }
}

