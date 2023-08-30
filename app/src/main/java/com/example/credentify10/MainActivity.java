package com.example.credentify10;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button accesoIssuer = (Button) findViewById(R.id.botonIssuer);


        accesoIssuer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Start NewActivity.class
                Intent myIntent = new Intent(getApplicationContext(), IssuerOptions.class);
                startActivity(myIntent);
            }
        });

        Button accesoHolder = (Button) findViewById(R.id.botonHolder);

        accesoHolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Start NewActivity.class
                Intent myIntent = new Intent(getApplicationContext(), HolderOptions.class);
                startActivity(myIntent);
            }
        });

    }

}