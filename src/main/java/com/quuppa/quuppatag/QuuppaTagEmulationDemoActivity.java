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
import android.location.Location;
import android.location.LocationManager;
import android.net.*;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.quuppa.tag.IntentAction;
import com.quuppa.tag.QuuppaTag;
import com.quuppa.tag.QuuppaTagService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class QuuppaTagEmulationDemoActivity extends Activity implements View.OnClickListener {
    private static final int REQUEST_ENABLE_BT = 1;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 2;
    private static final int WIFI_PERMISSION_REQUEST_CODE = 3;
    private static final int FINE_LOCATION_PERMISSION_REQUEST_CODE = 4;
    private static final int NEARBY_WIFI_DEVICES_PERMISSION_REQUEST_CODE = 5;

    private static final int BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 6;
    private static final String PREFS_IGNORE_BATTERY_OPTIMIZATION_ASKED = "IGNORE_BATTERY_OPTIMIZATION_ASKED";

    /** reference to the custom UI view that renders the pulsing Q */
    private PulsingQView pulsingView;
    private Switch backgroundModeSwitch;
    private TextView operationModeDescription;
    private boolean awaitingAlarmPermissionResult = false;

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
    private SharedPreferences preferences;
    private ConnectivityManager connectivityManager;
    private LocationManager locationManager;
    private final NetworkRequest networkRequest =
            new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(QuuppaTag.PREFS, Context.MODE_PRIVATE);
        connectivityManager = getSystemService(ConnectivityManager.class);

        if (Build.VERSION.SDK_INT >= 31) {
            // ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO in API 31, const value 1
            // without passing the flag, we couldn't read the SSID
            networkCallback = new ConnectivityManager.NetworkCallback(1) {
                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    onNetworkCapabilitiesChanged(network, networkCapabilities, this);
                }
            };
        }
        else {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
                    onNetworkCapabilitiesChanged(network, networkCapabilities, this);
                }
            };
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        setContentView(R.layout.activity_fullscreen);
        setActionBar(findViewById(R.id.toolbar));
        pulsingView = (PulsingQView) findViewById(R.id.pulsingQView);
        backgroundModeSwitch = findViewById(R.id.background_mode_switch);
        operationModeDescription = findViewById(R.id.operation_mode_description);

        pulsingView.setOnClickListener(this);

        // Use prefs directly, lib uses true as the default value
        updateBackgroundModeUI(preferences.getBoolean(QuuppaTag.PREFS_BACKGROUND_MODE, false));

        backgroundModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= 31) {
                    AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                        new AlertDialog.Builder(this)
                                .setTitle("Permission needed for Background Mode")
                                .setMessage("Background mode requires permission to schedule exact alarms for reliable operation and to conserve battery. Please grant this permission in the next screen.")
                                .setPositiveButton("OK", (dialog, which) -> {
                                    awaitingAlarmPermissionResult = true;
                                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(intent);
                                })
                                .setOnCancelListener(dialog -> backgroundModeSwitch.setChecked(false))
                                .show();
                        backgroundModeSwitch.setChecked(false);
                        return;
                    }
                }
            }
            QuuppaTag.setBackgroundMode(this, isChecked);
            updateBackgroundModeUI(isChecked);
            restartServiceIfEnabled();
        });

        if (QuuppaTag.getNotifiedActivityClass(this) == null)
            QuuppaTag.setNotifiedActivityClass(this, getClass());

        pulsingView.setIsPulsing(QuuppaTag.isServiceEnabled(this));
    }

    private void updateBackgroundModeUI(boolean backgroundMode) {
        backgroundModeSwitch.setChecked(backgroundMode);
        operationModeDescription.setText(backgroundMode ? R.string.background_mode_description : R.string.foreground_mode_description);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (awaitingAlarmPermissionResult) {
            awaitingAlarmPermissionResult = false;
            if (Build.VERSION.SDK_INT >= 31) {
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null && alarmManager.canScheduleExactAlarms()) {
                    QuuppaTag.setBackgroundMode(this, true);
                    updateBackgroundModeUI(true);
                    restartServiceIfEnabled();
                }
            }
        }

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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        MenuItem menuEnableOnlyWhen = menu.findItem(R.id.menu_enable_only_when);
        // Too many variations in permissions, getting SSID etc. just don't show this option for older devices
        if (Build.VERSION.SDK_INT < 33) menuEnableOnlyWhen.setVisible(false);
        else {
            menuEnableOnlyWhen.setChecked(preferences.contains(QuuppaTag.PREFS_SELECTED_LOCATION) || preferences.contains(QuuppaTag.PREFS_SELECTED_WIFI));
            menuEnableOnlyWhen.setEnabled(QuuppaTag.isServiceEnabled(this));
        }
        return super.onPrepareOptionsMenu(menu);
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
            case R.id.menu_enable_only_when:
                showEnableConditionsDialog();
                return true;
            case R.id.action_reset:
                preferences.edit().clear().apply();
                Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show();
                // re-initialize the view with default settings
                updateBackgroundModeUI(QuuppaTag.isBackgroundMode(this));
                // restart the service to apply the default settings
                restartServiceIfEnabled();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showEnableConditionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String locationSelection = preferences.getString(QuuppaTag.PREFS_SELECTED_LOCATION, null);
        String locationOption = locationSelection == null ?
                "only when within 1 km/ 0.6 mile radius of current location" :
                "only when within 1 km/ 0.6 mile radius of previously set location (" + locationSelection + ")";

        String wifiSelection = preferences.getString(QuuppaTag.PREFS_SELECTED_WIFI, null);

        String wifiOption;
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo currentWifi = wifiManager.getConnectionInfo();

        Log.d(QuuppaTagService.class.getSimpleName(), "Current Wifi info " + currentWifi);

        if (!wifiManager.isWifiEnabled() || currentWifi == null || currentWifi.getNetworkId() == -1) wifiOption = "Wi-Fi must be connected to limit to a specific Wi-Fi network";
        else wifiOption = wifiSelection == null ?
                "only when using currently connected Wi-Fi network" :
                "only when connected to Wi-Fi network " + wifiSelection;

        String[] options = {"always", locationOption, wifiOption};
        int selectedIdx = 0;
        if (locationSelection != null) selectedIdx = 1;
        else if (wifiSelection != null) selectedIdx = 2;

        AlertDialog alertDialog = builder.setTitle("Keep active")
                .setSingleChoiceItems(options, selectedIdx, (dialog, which) -> {
                    if (which == 1) {
                        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
                        else if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE);
                        else if (locationSelection == null) selectOnlyWhenLocation();
                    } else if (which == 2) {
                        if (checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED)
                            requestPermissions(new String[]{Manifest.permission.ACCESS_WIFI_STATE}, WIFI_PERMISSION_REQUEST_CODE);
                        else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_LOCATION_PERMISSION_REQUEST_CODE);
                        else if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)
                            requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, NEARBY_WIFI_DEVICES_PERMISSION_REQUEST_CODE);
                        else if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE);
                        else if (wifiSelection == null) selectOnlyWhenWifi();
                    } else {
                        removeOnlyWhenSelection();
                    }
                    dialog.dismiss();
                })
                .create();

        alertDialog.getListView().setOnHierarchyChangeListener(
                new ViewGroup.OnHierarchyChangeListener() {
                    @Override
                    public void onChildViewAdded(View parent, View child) {
                        CharSequence text = ((TextView)child).getText();
                        if (text.toString().startsWith("Wi-Fi must")) child.setEnabled(false);
                    }

                    @Override
                    public void onChildViewRemoved(View view, View view1) {
                    }
                });

        alertDialog.show();
    }

    private void removeOnlyWhenSelection() {
        SharedPreferences preferences = getSharedPreferences(QuuppaTag.PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(QuuppaTag.PREFS_SELECTED_LOCATION);
        editor.remove(QuuppaTag.PREFS_SELECTED_WIFI);
        editor.commit();
        Intent serviceIntent = new Intent(QuuppaTagEmulationDemoActivity.this, QuuppaTagService.class);
        serviceIntent.setAction(IntentAction.QT_ACTIVE_ONLY_CHANGED.fullyQualifiedName());
        startForegroundService(serviceIntent);
    }

    private void selectOnlyWhenLocation() {
        preferences.edit().remove(QuuppaTag.PREFS_SELECTED_WIFI).apply();
        List<String> providers = locationManager.getProviders(true);
        Location bestLocation = null;
        try {
            for (String provider : providers) {
                Location l = locationManager.getLastKnownLocation(provider);
                if (l == null) {
                    continue;
                }
                if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                    // Found best last known location: %s", l);
                    bestLocation = l;
                }
            }
        } catch (SecurityException e) {
            // handle with null bestLocation
        }
        if (bestLocation != null) {
            String locationString = bestLocation.getLatitude() + "," + bestLocation.getLongitude();
            preferences.edit().putString(QuuppaTag.PREFS_SELECTED_LOCATION, locationString).apply();
            Toast.makeText(this, "Active location lat,long set to: " + locationString, Toast.LENGTH_LONG).show();
        }
        else {
            preferences.edit().remove(QuuppaTag.PREFS_SELECTED_LOCATION).apply();
            Toast.makeText(this, "Couldn't get any last location, you must enable location services from Settings", Toast.LENGTH_LONG).show();
        }

        Intent serviceIntent = new Intent(this, QuuppaTagService.class);
        serviceIntent.setAction(IntentAction.QT_ACTIVE_ONLY_CHANGED.fullyQualifiedName());
        startForegroundService(serviceIntent);
    }

    private void selectOnlyWhenWifi() {
//        preferences.edit().remove(PREFS_LOCATION_RADIUS).apply();
        preferences.edit().remove(QuuppaTag.PREFS_SELECTED_LOCATION).apply();
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        connectivityManager.requestNetwork(networkRequest, networkCallback);
    }
    
/*
    private void showRadiusSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        int selectedIdx = preferences.getInt(PREFS_LOCATION_RADIUS, -1); // Default to -1 if no preference set
        String[] distances = {"1km/ 0.6 miles", "5 km/ 3.1 miles", "25 km/ 15.6 miles"};
        // Only have the remove option if one option is currently selected
        if (selectedIdx >= 0) {
            distances = Arrays.copyOf(distances, distances.length + 1);
            distances[distances.length-1] = "Remove & allow always enabled";
        }
        // need to be final
        final String[] options = distances;

        builder.setTitle("Select Distance")
                .setSingleChoiceItems(options, selectedIdx, (dialog, which) -> {
                    if (which < 0 || which > 2) {
                        preferences.edit().remove(PREFS_LOCATION_RADIUS).apply();
                        Toast.makeText(this, "Always enabled", Toast.LENGTH_SHORT).show();
                    } else {
                        preferences.edit().remove(QuuppaTag.PREFS_SELECTED_WIFI).apply();
                        preferences.edit().putInt(PREFS_LOCATION_RADIUS, (which + 1) * 4).apply();
                        List<String> providers = locationManager.getProviders(true);

                        Location l = null;
                        try {
                            for (int i = 0; i < providers.size(); i++) {
                                l = locationManager.getLastKnownLocation(providers.get(i));
                                if (l != null) {
                                    String locationString = l.getLatitude() + "," + l.getLongitude();
                                    preferences.edit().putString(QuuppaTag.PREFS_SELECTED_LOCATION, locationString).apply();
                                    Toast.makeText(this, "Active location lat,long set to: " + locationString, Toast.LENGTH_LONG).show();
                                    break;
                                }
                            }
                        } catch (SecurityException e) {
                            preferences.edit().remove(PREFS_LOCATION_RADIUS).apply();
                            preferences.edit().remove(QuuppaTag.PREFS_SELECTED_LOCATION).apply();
                            Toast.makeText(this, "Couldn't get any last location, you must enable location services from Settings", Toast.LENGTH_LONG).show();
                        }
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private void showWifiSelectionDialog() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo currentWifi = wifiManager.getConnectionInfo();

//        if ("<unknown ssid>".equalsIgnoreCase(currentWifi.getSSID())) {
//            Toast.makeText(this, "Cannot get ", Toast.LENGTH_SHORT).show();
//            return;
//        }

        if (currentWifi != null) {
            String wifiName = currentWifi.getSSID();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select Wi-Fi")
                    .setItems(new String[]{wifiName, "Remove & allow always enabled"}, (dialog, which) -> {
                        if (which == 1) {
                            preferences.edit().remove(QuuppaTag.PREFS_SELECTED_WIFI).apply();
                            Toast.makeText(this, "Always enabled", Toast.LENGTH_SHORT).show();
                        } else {
                            preferences.edit().remove(PREFS_LOCATION_RADIUS).apply();
                            preferences.edit().remove(QuuppaTag.PREFS_SELECTED_LOCATION).apply();
                            preferences.edit().putString(QuuppaTag.PREFS_SELECTED_WIFI, wifiName).apply();
                            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
                            connectivityManager.requestNetwork(networkRequest, networkCallback);
                        }
                        dialog.dismiss();
                    })
                    .show();
        } else {
            Toast.makeText(this, "No Wi-Fi connected", Toast.LENGTH_SHORT).show();
        }
    }
 */

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
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_ENABLE_BT);
                    return false;
                }
            }

            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return false;
        }

        if (Build.VERSION.SDK_INT >= 31) // Build.VERSION_CODES.SNOW_CONE
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADVERTISE}, 1);
                return false;
            }

        Intent tagServiceIntent = new Intent(this, QuuppaTagService.class);
        startForegroundService(tagServiceIntent);
        QuuppaTag.setServiceEnabled(this, true);

        if (QuuppaTag.isBackgroundMode(this)) if (!preferences.getBoolean(PREFS_IGNORE_BATTERY_OPTIMIZATION_ASKED, false)) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            final String packageName = getPackageName();
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                // Show dialog to prompt user to disable battery optimization
                new AlertDialog.Builder(this)
                        .setTitle("Disable Battery Optimization")
                        .setMessage("We noticed that battery optimizations are not ignored for this application. "
                                + "This may result in the OS terminating the Quuppa tag service while the app is in "
                                + " the background. Would you like to disable battery optimizations for this app?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            // Open settings to disable battery optimization
                            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + packageName));
                            startActivity(intent);
                        })
                        .setNegativeButton("No", (dialog, which) -> {
                            preferences.edit().putBoolean(PREFS_IGNORE_BATTERY_OPTIMIZATION_ASKED, true).apply();
                        })
                        .show();
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show();
            // Make sure the only place where this is requested is when user chooses the menu item, otherwise need
            // more complex to know where the user was when request was invoked
            else {
                // ACCESS_BACKGROUND_LOCATION doesn't exist before API level 29
                if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE);
                else selectOnlyWhenLocation();
            }
        }
        else if (requestCode == WIFI_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Wi-Fi state permission is required", Toast.LENGTH_SHORT).show();
            // Make sure the only place where this is requested is when user chooses the menu item, otherwise need
            // more complex to know where the user was when request was invoked
            else {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_LOCATION_PERMISSION_REQUEST_CODE);
                else if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, NEARBY_WIFI_DEVICES_PERMISSION_REQUEST_CODE);
                else selectOnlyWhenWifi();
            }
        }
        else if (requestCode == FINE_LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Fine location permission is required to access connected Wi-Fi SSID", Toast.LENGTH_SHORT).show();
                // Make sure the only place where this is requested is when user chooses the menu item, otherwise need
                // more complex to know where the user was when request was invoked
            else {
                if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.NEARBY_WIFI_DEVICES}, NEARBY_WIFI_DEVICES_PERMISSION_REQUEST_CODE);
                else selectOnlyWhenWifi();
            }
        }
        else if (requestCode == NEARBY_WIFI_DEVICES_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Nearby wifi devices is required to access connected Wi-Fi SSID", Toast.LENGTH_SHORT).show();
            // We need ACCESS_WIFI_STATE and ACCESS_FINE_LOCATION before we can read SSID
            // We need ACCESS_BACKGROUND_LOCATION to read SSID in the service
            else {
                // ACCESS_BACKGROUND_LOCATION doesn't exist before API level 29
                if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE);
                else selectOnlyWhenWifi();
            }
        }
        else if (requestCode == BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "Permission to access location in the background is required for this feature", Toast.LENGTH_LONG).show();
            else {
                // We can get to this from either of the two "active only when..." menu items, they both shouldn't have value at the same time
                if (QuuppaTag.getSelectedWifi(this) != null) selectOnlyWhenWifi();
                else if (QuuppaTag.getSelectedLocation(this) != null) selectOnlyWhenLocation();
            }
        }
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

    private void onNetworkCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities, ConnectivityManager.NetworkCallback networkCallback) {
        // Don't allow calling this operation from device with API level < 29
        try {
            Method method = NetworkCapabilities.class.getMethod("getTransportInfo", new Class<?>[]{});
            WifiInfo wifiInfo = (WifiInfo) method.invoke(networkCapabilities);
            // TODO if SSID is null, remove the preference?
            String ssid= wifiInfo.getSSID();
            if (ssid == null) preferences.edit().remove(QuuppaTag.PREFS_SELECTED_WIFI).apply();
            else preferences.edit().putString(QuuppaTag.PREFS_SELECTED_WIFI, wifiInfo.getSSID()).apply();
            Intent serviceIntent = new Intent(QuuppaTagEmulationDemoActivity.this, QuuppaTagService.class);
            serviceIntent.setAction(IntentAction.QT_ACTIVE_ONLY_CHANGED.fullyQualifiedName());
            startForegroundService(serviceIntent);

            runOnUiThread(() -> {
                if (ssid != null) Toast.makeText(QuuppaTagEmulationDemoActivity.this, "Active only on " + ssid, Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {}

        connectivityManager.unregisterNetworkCallback(networkCallback);
    }
}

