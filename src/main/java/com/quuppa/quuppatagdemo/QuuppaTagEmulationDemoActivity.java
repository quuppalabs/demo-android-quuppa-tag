/**
 * Quuppa Android Tag Emulation Demo application.
 * <p/>
 * Copyright 2022 Quuppa Oy
 * <p/>
 * Disclaimer
 * THE SOURCE CODE, DOCUMENTATION AND SPECIFICATIONS ARE PROVIDED “AS IS”. ALL LIABILITIES, WARRANTIES AND CONDITIONS, EXPRESS OR IMPLIED,
 * INCLUDING WITHOUT LIMITATION TO THOSE CONCERNING MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT
 * OF THIRD PARTY INTELLECTUAL PROPERTY RIGHTS ARE HEREBY EXCLUDED.
 */
package com.quuppa.quuppatagdemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import com.quuppa.tag.QuuppaTag;
import com.quuppa.tag.QuuppaTagException;


import java.util.Arrays;


public class QuuppaTagEmulationDemoActivity extends Activity implements View.OnClickListener {
    /** For logging */
    private static final String TAG = "QuuppaTagEmulDemoActiv";
    /** Name of the stored preferences */
    public static final String PREFS_NAME = "MyPrefsFile";
    /** Name of the stored manual tag id */
    public static final String PREFS_MANUAL_TAG_ID = "tagID";
    /** name of the stored advertizing mode pref */
    public static final String ADV_MODE = "advMode";
    /** name of the stored advertizing tx power pref */
    public static final String ADV_TX_POWER = "advTxPower";
    /** Display names of the Adventizing Modes */
    private final String[] advModes = new String[]{"ADVERTISE_MODE_LOW_POWER", "ADVERTISE_MODE_BALANCED", "ADVERTISE_MODE_LOW_LATENCY"};
    /** Display names of the various TX power settings */
    private final String[] advTxPowers = new String[]{"ADVERTISE_TX_POWER_ULTRA_LOW", "ADVERTISE_TX_POWER_LOW", "ADVERTISE_TX_POWER_MEDIUM", "ADVERTISE_TX_POWER_HIGH"};

    /** reference to the #BluetoothLeAdvertiser */
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    /** reference to the custom UI view that renders the pulsing Q */
    private PulsingQView pulsingView;
    /** flag indicating whether Direction Packet advertising has been started */
    private boolean dfPacketAdvRunning;

    /**
     * Callback used for Direction Finding Packet advertisements.
     */
    private AdvertiseCallback dfPacketAdvCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);
        pulsingView = (PulsingQView) findViewById(R.id.pulsingQView);
        // make this listen to view's clicks...
        pulsingView.setOnClickListener(this);


        dfPacketAdvCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings advertiseSettings) {
                final String message = "Broadcasting with tagID: " + getTagId() + " (You can change the id in settings)";
                Log.d("AdvCallback", message);
                QuuppaTagEmulationDemoActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(QuuppaTagEmulationDemoActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
                pulsingView.setIsPulsing(true);
            }

            @Override
            public void onStartFailure(int i) {
                final String message = "Start broadcast failed error code: " + i;
                Log.e("AdvCallback", message);
                QuuppaTagEmulationDemoActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(QuuppaTagEmulationDemoActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
                pulsingView.setIsPulsing(false);
            }
        };
    }

    protected void onDestroy() {
        // Manage the state of the advertisement as part of activity's lifecycle
        if (dfPacketAdvCallback != null) QuuppaTag.stopAdvertising(this, dfPacketAdvCallback);
        dfPacketAdvRunning = false;
        dfPacketAdvCallback = null;
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_setTagID:
                setTagId();
                return true;
            case R.id.action_showAbout:
                //Intent intent = new Intent(this, AboutScreenActivity.class);
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                alert.setTitle("Quuppa Tag Emulation Demo app");
                alert.setMessage("Version 2.0, Copyright 2022 Quuppa Oy");

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
            toggleDFPacketAdv();
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

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
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
                // persist!
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString(PREFS_MANUAL_TAG_ID, manualID.getText().toString());
                editor.apply();

                if (dfPacketAdvRunning) {
                    toggleDFPacketAdv();
                    toggleDFPacketAdv();
                }
                dialog.dismiss();
            }
        });
    }

    private String getTagId() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String tagId = settings.getString(PREFS_MANUAL_TAG_ID, null);
        return (tagId == null || tagId.length() < 12) ? QuuppaTag.getGeneratedTagId(this) : tagId;
    }

    /**
     * Toggles the Direction Finding Packet broadcast on/off.
     */
    private void toggleDFPacketAdv() {
        if (!dfPacketAdvRunning) {
            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            String tagID = getTagId();
            if (tagID == null) {
                Toast.makeText(QuuppaTagEmulationDemoActivity.this, "Tag ID not set, please enter it in the settings!", Toast.LENGTH_LONG).show();
                return;
            }
            QuuppaTag.DEVICE_ID = tagID;
            /**
             * Advertise mode and tx power are fixed to LOW_LATENCY and HIGH power. Adjusting these settings affect the positioning quality and should only be changed with consideration
             */
            int mode = settings.getInt(ADV_MODE, AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
            int tx = settings.getInt(ADV_TX_POWER, AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
            try {
                QuuppaTag.startAdvertising(this, dfPacketAdvCallback, true, mode, tx);
                dfPacketAdvRunning = true;
            } catch (QuuppaTagException e) {
                final String message = "Starting Bluetooth advertising failed. " + e.getMessage();
                Log.d("AdvCallback", message);
                QuuppaTagEmulationDemoActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(QuuppaTagEmulationDemoActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });

                e.printStackTrace();
            }
        } else {
            QuuppaTag.stopAdvertising(this, dfPacketAdvCallback);
            dfPacketAdvRunning = false;
            pulsingView.setIsPulsing(false);
        }
    }

}

