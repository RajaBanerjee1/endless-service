package com.robertohuertas.endless;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.asce.trackme.LocationTrackService;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.view.View;
import android.widget.Button;

import java.util.ArrayList;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static com.robertohuertas.endless.ServiceTrackerKt.getServiceState;
import static com.robertohuertas.endless.UtilsKt.log;

public class MainActivity2 extends AppCompatActivity {

    private ArrayList<String> permissionsToRequest;
    private ArrayList<String> permissionsRejected = new ArrayList<>();
    private ArrayList<String> permissions = new ArrayList<>();

    private final static int ALL_PERMISSIONS_RESULT = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();



                permissions.add(ACCESS_FINE_LOCATION);
                permissions.add(ACCESS_COARSE_LOCATION);

                permissionsToRequest = findUnAskedPermissions(permissions);

                if (permissionsToRequest.size() > 0)
                    requestPermissions(permissionsToRequest.toArray(new String[permissionsToRequest.size()]), ALL_PERMISSIONS_RESULT);
                else {
                    actionOnService(Actions.START);
                }
            }
        });
/*
        Button startButton = findViewById(R.id.startActivityButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Starting service", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                actionOnService(Actions.START);
            }
        });

        Button stopButton = findViewById(R.id.buttonStop);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Stopping service", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

                actionOnService(Actions.STOP);
            }
        });
  */
    }

    private boolean hasPermission(String permission) {
        if (canMakeSmores()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
            }
        }
        return true;
    }
    private boolean canMakeSmores() {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1);
    }

    private ArrayList<String> findUnAskedPermissions(ArrayList<String> wanted) {
        ArrayList<String> result = new ArrayList<String>();

        for (String perm : wanted) {
            if (!hasPermission(perm)) {
                result.add(perm);
            }
        }

        return result;
    }

    private void actionOnService( Actions action) {
        //if (getServiceState(this) == ServiceState.STOPPED) {
            Intent it = new Intent(this, LocationTrackService.class);
            it.setAction(action.name());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                log("Starting the service in >=26 Mode");
                startForegroundService(it);
                return;
            }
            log("Starting the service in < 26 Mode");
            startService(it);
        //}
        }
    }
