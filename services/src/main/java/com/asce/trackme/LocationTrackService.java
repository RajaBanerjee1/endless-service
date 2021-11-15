package com.asce.trackme;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.R;
import com.loopj.android.http.RequestParams;

import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;


public class LocationTrackService extends Service implements LocationListener {

    private Context mContext;
    private static String TAG = "LocationTrackService";
    private static String locationPostUrl = "https://aurorascienceexploration.com:3001/postLocation";

    boolean checkGPS = false;

    private static Timer timer = new Timer();

    boolean checkNetwork = false;

    boolean canGetLocation = false;

    Location loc;
    //    double latitude;
//    double longitude;
    Location lastPostedLocation;

    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10;

    private static final long MIN_TIME_BW_UPDATES = 1000 * 60 * 1;
    protected LocationManager locationManager;
    private static long PostLocationPeriodMillis =  60 *1000 ;
    private  PowerManager.WakeLock wakeLock= null ;
    private boolean isServiceStarted = false;

    /*
    public LocationTrackService(Context mContext) {
        this.mContext = mContext;
        getLocation();
    }
*/
    public class MyLocalBinder extends Binder {
        public LocationTrackService getService() {
            return LocationTrackService.this;
        }
    }

    public void onCreate()
    {
        super.onCreate();

        if (isServiceStarted) return;
        // log.d("Starting the foreground service task")
        //Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show();
        isServiceStarted = true;
        //setServiceState(this, ServiceState.STARTED);

        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocationTrackService::lock");
        wakeLock.acquire();


        startTokenRenewerTimer();

        Notification notification = createNotification();
        startForeground(1, notification);

    }


    private Notification createNotification() {
        String notificationChannelId = "LOCATION TRACK CHANNEL";

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE) ;
            NotificationChannel channel = new NotificationChannel(
                    notificationChannelId,
                    "Endless Service notifications channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
                    channel.setDescription("Location track channel");
/*
                    .let {
                it.description = "Endless Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it
            }
            */


            notificationManager.createNotificationChannel(channel);
        }
/*
        PendingIntent pendingIntent  = new Intent(this, MainActivity2.class);
        let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
        }
*/
        Notification.Builder builder  ;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            builder = new Notification.Builder(
                this,
                notificationChannelId
            );
        }
        else {
            builder = new Notification.Builder (this);
        }


        return builder
                .setContentTitle("Endless Service")
                .setContentText("This is your favorite endless service working")
                .setTicker("Ticker text")
                .setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
                .build();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        Intent restartServiceIntent = new Intent(getApplicationContext(),
                this.getClass());
        restartServiceIntent.setPackage(getPackageName());


        PendingIntent restartServicePendingIntent = PendingIntent.getService(
                getApplicationContext(), 1, restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT);
        AlarmManager alarmService = (android.app.AlarmManager) getApplicationContext()
                .getSystemService(Context.ALARM_SERVICE);
        alarmService.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG , "Received start id " + startId + ": " + intent);
        return START_STICKY;
    }

    private class mainTask extends TimerTask
    {
        public void run()
        {
            timerTaskHandler.sendEmptyMessage(0);
        }
    }

    private final Handler timerTaskHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            try {

                if(loc == null)
                    loc = getLocation(getApplicationContext());

                if (loc != null &&  ( lastPostedLocation == null || !(lastPostedLocation.getLongitude() == loc.getLongitude() && lastPostedLocation.getLatitude() == loc.getLatitude())))
                    PostLocation(getApplicationContext(), loc);

            } catch (Exception mmsException) {
                Log.e(TAG, "Exception posting location " + mmsException.toString());
            }
        }
    };

    public void onDestroy()
    {
        super.onDestroy();

        if(wakeLock != null)
        {
            if(wakeLock.isHeld())
                wakeLock.release();
        }

        Log.d(TAG," Service Stopped");
    }


    private void startTokenRenewerTimer()
    {
        timer.scheduleAtFixedRate(new mainTask(), 0, PostLocationPeriodMillis);
        Log.d(TAG,"Started");
    }

    public Location getLocation(Context context) {

        mContext = context;

        try {
            locationManager = (LocationManager) mContext
                    .getSystemService(LOCATION_SERVICE);

            // get GPS status
            checkGPS = locationManager
                    .isProviderEnabled(LocationManager.GPS_PROVIDER);

            // get network provider status
            checkNetwork = locationManager
                    .isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!checkGPS && !checkNetwork) {
                Toast.makeText(mContext, "No Service Provider is available", Toast.LENGTH_SHORT).show();
            } else {
                this.canGetLocation = true;

                // if GPS Enabled get lat/long using GPS Services
                if (checkGPS) {

                    if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                    }
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                    if (locationManager != null) {
                        loc = locationManager
                                .getLastKnownLocation(LocationManager.GPS_PROVIDER);

                    }


                }


                /*if (checkNetwork) {


                    if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                    }
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);

                    if (locationManager != null) {
                        loc = locationManager
                                .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                    }

                    if (loc != null) {
                        latitude = loc.getLatitude();
                        longitude = loc.getLongitude();
                    }
                }*/

            }


        } catch (Exception e) {
            e.printStackTrace();
        }

        return loc;
    }

    public double getLongitude() {
        if (loc != null) {
            return loc.getLongitude();
        }
        return 0;
    }

    public double getLatitude() {
        if (loc != null) {
            return loc.getLatitude();
        }
        return 0;
    }

    public boolean canGetLocation() {
        return this.canGetLocation;
    }

    public void showSettingsAlert() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(mContext);


        alertDialog.setTitle("GPS is not Enabled!");

        alertDialog.setMessage("Do you want to turn on GPS?");


        alertDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                mContext.startActivity(intent);
            }
        });


        alertDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });


        alertDialog.show();
    }

    public void PostLocation(Context context,final Location location)
    {
        String androidID =         Settings.Secure.getString(getApplicationContext().getContentResolver(), Settings.Secure.ANDROID_ID);

        AsyncHttpClient client = new AsyncHttpClient();

        client.addHeader("authorization",androidID);

        RequestParams params = new RequestParams();

        params.put("androidID", androidID);
        params.put("deviceType","A");
        params.put("latitude", location.getLatitude());
        params.put("longitude", location.getLongitude());

        Log.d(TAG,"About to post location");

        //upload files
        client.post(context, locationPostUrl, params, new AsyncHttpResponseHandler()  {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onSuccess(int statusCode, cz.msebera.android.httpclient.Header[] headers,
                                  byte[] responseBody) {
                //Work to be done after successful upload
                Toast.makeText(getApplicationContext(), "Upload Successful", Toast.LENGTH_LONG).show();

                Log.d(TAG, "Upload Successful. Response Body " + new String(responseBody, StandardCharsets.UTF_8));
                lastPostedLocation = location;

                // progress.setProgress(0);
                try {


                } catch (Exception mmsException) {
                    Log.e(TAG, "Exception sending MMS after successful upload " + mmsException.toString());
                }
            }


            @Override
            public void onFailure(int statusCode, cz.msebera.android.httpclient.Header[] headers,
                                  byte[] responseBody, Throwable error) {
                //Do work after uploading failure
                Toast.makeText(getApplicationContext(), "Upload failed", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Response Body "  + new String( responseBody, StandardCharsets.UTF_8));

            }


            public void onProgress(int bytesWritten, int totalSize) {

                // TODO Auto-generated method stub
                //super.onProgress(bytesWritten, totalSize);
                int count = (int) ((bytesWritten * 1.0 / totalSize) * 100);
                //Upload progress display
                //progress.setProgress(count);
                Log.e("Upload Progress >>>>>", bytesWritten + " / " + totalSize);
            }

            @Override
            public void onRetry(int retryNo) {
                // TODO Auto-generated method stub
                super.onRetry(retryNo);
                //return the number of retries
            }
        });
    }


    public void stopListener() {
        if (locationManager != null) {

            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            locationManager.removeUpdates(LocationTrackService.this);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }
}
