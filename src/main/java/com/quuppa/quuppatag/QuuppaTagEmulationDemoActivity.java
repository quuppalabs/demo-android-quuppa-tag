/**
 * Quuppa Android Tag Emulation Demo application.
 * <p/>
 * Copyright 2025 Quuppa Oy
 * <p/>
 * Disclaimer
 * THE SOURCE CODE, DOCUMENTATION AND SPECIFICATIONS ARE PROVIDED “AS IS”. ALL LIABILITIES, WARRANTIES AND CONDITIONS, EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION TO THOSE CONCERNING MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT
 * OF THIRD PARTY INTELLECTUAL PROPERTY RIGHTS ARE HEREBY EXCLUDED.
 */
package com.quuppa.quuppatag;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertisingSetParameters;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.quuppa.tag.IntentAction;
import com.quuppa.tag.QuuppaTag;
import com.quuppa.tag.QuuppaTagService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class QuuppaTagEmulationDemoActivity extends Activity implements View.OnClickListener {
    private static final int REQUEST_ENABLE_BT = 1;

    /** reference to the custom UI view that renders the pulsing Q */
    private PulsingQView pulsingView;

    private BroadcastReceiver serviceBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                IntentAction intentAction = IntentAction.fullyQualifiedValueOf(intent.getAction());
                // Ensure the Toast is displayed on the UI thread
                runOnUiThread(() -> {
                    String message = null;
                    if (IntentAction.QT_STARTED.equals(intentAction))
                        message = "Broadcasting with tagID: " + getTagId();
                    else if (IntentAction.QT_STOPPED.equals(intentAction))
                        message = "Broadcasting stopped";
                    else if (IntentAction.QT_SYSTEM_ERROR.equals(intentAction)) {
                        String error = intent.getStringExtra("error");
                        message = "Start broadcast failed" + (error == null ? "" : " with error: " + error);
                    }
                    if (message != null) Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                // unknown intent, ignore
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);
        pulsingView = (PulsingQView) findViewById(R.id.pulsingQView);

        pulsingView.setOnClickListener(this);

        if (QuuppaTag.getNotifiedActivityClass(this) == null)
            QuuppaTag.setNotifiedActivityClass(this, getClass());

        pulsingView.setIsPulsing(QuuppaTag.isServiceEnabled(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        for (IntentAction action : IntentAction.values()) {
            filter.addAction(action.fullyQualifiedName());
        }
        // RECEIVER_NOT_EXPORTED requires target package to be set in intent
        // intent.setPackage(context.packageName)
        // https://issuetracker.google.com/issues/293487554
        // As a generic library, better use generic events
        registerReceiver(serviceBroadcastReceiver, filter,  RECEIVER_EXPORTED);
    }

    @Override
    protected void onPause() {
        unregisterReceiver(serviceBroadcastReceiver);
        super.onPause();
    }

    protected void onDestroy() {
        super.onDestroy();
        // Check if battery optimization is enabled for this app
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        final String packageName = getPackageName();
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            // Show dialog to prompt user to disable battery optimization
            new AlertDialog.Builder(this)
                    .setTitle("Disable Battery Optimization")
                    .setMessage("We noticed that battery optimizations are not ignored for this application. "
                            + "This may result in the OS terminating the Quuppa tag service while the app is in "
                            + " the background. Would you like to disable battery optimizations for this app?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Open settings to disable battery optimization
                            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + packageName));
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("No", null)
                    .show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @SuppressLint("WrongConstant")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_setTagID:
                setTagId();
                return true;
// Selecting tx power is implemented but there's no particular reason to give user the option to change it
//            case R.id.action_select_tx_power:
//                showTxPowerSelectionDialog();
//                return true;
            case R.id.action_showAbout:
                //Intent intent = new Intent(this, AboutScreenActivity.class);
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                alert.setTitle("Quuppa Tag Emulation Demo app, version " + BuildConfig.VERSION_NAME);
                try {
                    InputStream is = getResources().getAssets().open("about.txt");
                    byte[] buffer = new byte[10240];
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    for (int length = is.read(buffer); length != -1; length = is.read(buffer)) {
                        baos.write(buffer, 0, length);
                    }
                    is.close();
                    baos.close();

                    // alert.setMessage(new String(baos.toByteArray(), StandardCharsets.UTF_8));
                    TextView messageView = new TextView(this);
                    messageView.setText(new String(baos.toByteArray(), StandardCharsets.UTF_8));
                    messageView.setPadding(30, 20, 30, 20);
                    messageView.setJustificationMode(2); // LineBreaker.JUSTIFICATION_MODE_INTER_WORD); only availabe in 29
                    // Also, INTER_WORD didn't seem to do anything, 2 is ...INTER_CHARACTER

                    ScrollView scrollView = new ScrollView(this);
                    scrollView.addView(messageView);
                    alert.setView(scrollView);
                } catch (IOException e) {
                    alert.setMessage("Version 2.0, Copyright 2025 Quuppa Oy. <about.txt wasn't available>");
                }

                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                });
                alert.show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    public void onClick(View v) {
        if (pulsingView.equals(v))
            toggleQuuppaTagService();
    }

    /**
     * Shows a dialog for the user to set the Tag ID and persists that to Android Shared Preferences
     */
    private void setTagId() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set Quuppa Tag ID");

        LayoutInflater inflater = this.getLayoutInflater();
        final View view = inflater.inflate(R.layout.set_tag_id_dialog, null);
        builder.setView(view);

        final EditText manualID = (EditText) view.findViewById(R.id.manualID);

        manualID.setText(getTagId());

        // add buttons
        builder.setPositiveButton("Ok", null);
        builder.setNegativeButton("Cancel", null);

        final AlertDialog dialog = builder.create();
        dialog.show();

        //Overriding the handler immediately so that we can do validation and control dismissal of the dialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String tagID = manualID.getText().toString();
                if (tagID.length() != 12) {
                    Toast.makeText(QuuppaTagEmulationDemoActivity.this, "Tag ID must be 12 characters!", Toast.LENGTH_LONG).show();
                    return;
                }
                if (!tagID.matches("[a-f0-9]{12}")) {
                    Toast.makeText(QuuppaTagEmulationDemoActivity.this, "Tag ID must be only hex characters! [0-9,a-f]", Toast.LENGTH_LONG).show();
                    return;
                }

                QuuppaTag.setTagId(QuuppaTagEmulationDemoActivity.this, manualID.getText().toString());
                restartServiceIfEnabled();
                dialog.dismiss();
            }
        });
    }

    private String getTagId() {
        return QuuppaTag.getOrInitTagId(this);
    }

    private void showTxPowerSelectionDialog() {
        final int[] txPowers = {
                AdvertisingSetParameters.TX_POWER_HIGH,
                AdvertisingSetParameters.TX_POWER_MEDIUM,
                AdvertisingSetParameters.TX_POWER_LOW,
                AdvertisingSetParameters.TX_POWER_ULTRA_LOW
        };
        final String[] txPowerLabels = {
                "High", "Medium", "Low", "Ultra Low"
        };

        int currentTxPower = QuuppaTag.getAdvertisingSetTxPower(this);
        int currentIndex = 0;
        for (int i = 0; i < txPowers.length; i++) {
            if (txPowers[i] == currentTxPower) {
                currentIndex = i;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select TX Power")
                .setSingleChoiceItems(txPowerLabels, currentIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        QuuppaTag.setAdvertisingSetTxPower(getApplicationContext(), txPowers[which]);
                        restartServiceIfEnabled();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restartServiceIfEnabled() {
        if (QuuppaTag.isServiceEnabled(this)) QuuppaTag.restart(QuuppaTagEmulationDemoActivity.this);
    }

    private boolean startServiceWithPermissionCheck() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show();
            return false;
        }
        else if (!bluetoothAdapter.isEnabled()) {
            if (Build.VERSION.SDK_INT >= 31) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
                    return false;
                }
            }

            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return false;
        }

        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                new AlertDialog.Builder(this)
                        .setTitle("Permission needed to schedule alarms")
                        .setMessage("The app relies on the accelerometer to know when the device is moving. "
                                + "Inversely, the app needs to schedule a timed event (alarm) to set state back to stationary "
                                + "and to slow the rate of advertisements. This reduces battery consumption.")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Prompt user to grant the schedule exact alarm
                                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        })
                        .show();
                return false;
            }
        }

        if (Build.VERSION.SDK_INT >= 31) // Build.VERSION_CODES.SNOW_CONE
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADVERTISE}, 1);
                return false;
            }
        Intent tagServiceIntent = new Intent(this, QuuppaTagService.class);
        startForegroundService(tagServiceIntent);
        QuuppaTag.setServiceEnabled(this, true);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) toggleQuuppaTagService();
            else Toast.makeText(this, "This app does not work with Bluetooth disabled", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Toggles the Direction Finding Packet broadcast on/off.
     */
    private void toggleQuuppaTagService() {
        boolean enabled = !QuuppaTag.isServiceEnabled(this);

        if (enabled) enabled = startServiceWithPermissionCheck();
        else QuuppaTag.stop(this);
        pulsingView.setIsPulsing(enabled);
    }

}

