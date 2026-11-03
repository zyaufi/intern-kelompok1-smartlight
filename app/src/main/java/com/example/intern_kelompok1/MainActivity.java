package com.example.intern_kelompok1;

import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;



public class MainActivity extends AppCompatActivity implements ZeroMQService.Listener {


    private LinearLayout pillStatus;
    private View dotStatus;
    private TextView tvConnectionStatus, tvVoltage, tvVoltageUnit, tvLedStatus;
    private View dotLed;
    private TextView tvServerInfo;


    private Button btnConnect, btnOn, btnOff;


    private Switch switchMode;
    private SeekBar seekBrightness;
    private TextView tvBrightnessValue, tvModeLabel, tvManualHint;
    private ProgressBar pbVoltage;

    private ZeroMQService zeroMQService;
    private SharedPreferences prefs;
    private boolean ledIsOn = false;
    private boolean isManualMode = false;
    private int brightnessLevel = 50;

    private static final String PREF_NAME = "SmartLightPrefs";
    private static final String KEY_IP = "server_ip";
    private static final String KEY_PORT = "server_port";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        zeroMQService = new ZeroMQService(this);

        setupListeners();
        restoreSavedServer();
    }

    private void bindViews() {
        pillStatus = findViewById(R.id.pillStatus);
        dotStatus = findViewById(R.id.dotStatus);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvServerInfo = findViewById(R.id.tvServerInfo);
        tvVoltage = findViewById(R.id.tvVoltage);
        tvVoltageUnit = findViewById(R.id.tvVoltageUnit);
        tvLedStatus = findViewById(R.id.tvLedStatus);
        dotLed = findViewById(R.id.dotLed);
        pbVoltage = findViewById(R.id.pbVoltage);

        btnConnect = findViewById(R.id.btnConnect);
        btnOn = findViewById(R.id.btnOn);
        btnOff = findViewById(R.id.btnOff);

        switchMode = findViewById(R.id.switchMode);
        seekBrightness = findViewById(R.id.seekBrightness);
        tvBrightnessValue = findViewById(R.id.tvBrightnessValue);
        tvModeLabel = findViewById(R.id.tvModeLabel);
        tvManualHint = findViewById(R.id.tvManualHint);


        pbVoltage.setMax(330);
    }

    private void setupListeners() {
        btnConnect.setOnClickListener(v -> showIpDialog());

        btnOn.setOnClickListener(v -> {
            if (!zeroMQService.isConnected()) {
                Toast.makeText(this, "Belum terhubung ke server", Toast.LENGTH_SHORT).show();
                return;
            }
            zeroMQService.sendCommand("ON");
            if (isManualMode) zeroMQService.sendCommand("BRIGHT:" + brightnessLevel);
            ledIsOn = true;
            updateLedUI();
        });

        btnOff.setOnClickListener(v -> {
            if (!zeroMQService.isConnected()) {
                Toast.makeText(this, "Belum terhubung ke server", Toast.LENGTH_SHORT).show();
                return;
            }
            zeroMQService.sendCommand("OFF");
            ledIsOn = false;
            updateLedUI();
        });

        switchMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isManualMode = isChecked;
            seekBrightness.setEnabled(isChecked);

            if (isChecked) {
                // Mode MANUAL
                tvModeLabel.setText("MANUAL");
                tvModeLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_manual));
                tvManualHint.setText("Potensio diabaikan — kontrol via slider");
                tvManualHint.setTextColor(ContextCompat.getColor(this, R.color.accent_manual));
                zeroMQService.sendCommand("MODE:MANUAL");
                seekBrightness.setAlpha(1f);
            } else {

                tvModeLabel.setText("AUTO (Potensio)");
                tvModeLabel.setTextColor(ContextCompat.getColor(this, R.color.accent_auto));
                tvManualHint.setText("Aktifkan switch untuk kontrol manual");
                tvManualHint.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                zeroMQService.sendCommand("MODE:AUTO");
                seekBrightness.setAlpha(0.4f);
            }


            tvModeLabel.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
        });

        seekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brightnessLevel = progress;
                tvBrightnessValue.setText(progress + "%");
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (zeroMQService.isConnected() && isManualMode && ledIsOn) {
                    zeroMQService.sendCommand("BRIGHT:" + brightnessLevel);
                }
            }
        });
    }

    private void restoreSavedServer() {
        String savedIp = prefs.getString(KEY_IP, "192.168.1.10");
        int savedPort = prefs.getInt(KEY_PORT, 5555);
        tvServerInfo.setText("Server: " + savedIp + ":" + savedPort);
        seekBrightness.setAlpha(0.4f); // default disabled look
    }

    private void showIpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Konfigurasi Server");

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_server_ip, null);
        EditText etIp = dialogView.findViewById(R.id.etIpAddress);
        EditText etPort = dialogView.findViewById(R.id.etPort);

        etIp.setText(prefs.getString(KEY_IP, "192.168.1.10"));
        etPort.setText(String.valueOf(prefs.getInt(KEY_PORT, 5555)));

        builder.setView(dialogView);
        builder.setPositiveButton("Hubungkan", (dialog, which) -> {
            String ip = etIp.getText().toString().trim();
            String portStr = etPort.getText().toString().trim();

            if (ip.isEmpty() || portStr.isEmpty()) {
                Toast.makeText(this, "IP dan Port wajib diisi", Toast.LENGTH_SHORT).show();
                return;
            }

            int port = Integer.parseInt(portStr);
            prefs.edit().putString(KEY_IP, ip).putInt(KEY_PORT, port).apply();
            tvServerInfo.setText("Server: " + ip + ":" + port);

            updateConnectionUI(false, "MENGHUBUNGKAN...");
            zeroMQService.connect(ip, port);
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private void updateConnectionUI(boolean connected, String text) {
        if (connected) {
            pillStatus.setBackgroundResource(R.drawable.bg_pill_connected);
            dotStatus.getBackground().setColorFilter(
                    ContextCompat.getColor(this, R.color.status_connected),
                    PorterDuff.Mode.SRC_IN);
            tvConnectionStatus.setText("TERHUBUNG");
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
        } else {
            pillStatus.setBackgroundResource(R.drawable.bg_pill_disconnected);
            dotStatus.getBackground().setColorFilter(
                    ContextCompat.getColor(this, R.color.status_disconnected),
                    PorterDuff.Mode.SRC_IN);
            tvConnectionStatus.setText(text != null ? text : "TERPUTUS");
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
        }
    }

    private void updateLedUI() {
        if (ledIsOn) {
            tvLedStatus.setText("ON");
            tvLedStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
            dotLed.getBackground().setColorFilter(
                    ContextCompat.getColor(this, R.color.status_connected),
                    PorterDuff.Mode.SRC_IN);
        } else {
            tvLedStatus.setText("OFF");
            tvLedStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            dotLed.getBackground().setColorFilter(
                    0xFF9E9E9E, PorterDuff.Mode.SRC_IN);
        }
    }



    @Override
    public void onConnected() {
        updateConnectionUI(true, "TERHUBUNG");
        Toast.makeText(this, "Terhubung ke gateway!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisconnected(String reason) {
        updateConnectionUI(false, "TERPUTUS");
        Toast.makeText(this, "Koneksi gagal: " + reason, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onVoltageReceived(String voltage) {
        tvVoltage.setText(voltage);
        // Update progress bar (konversi "2.45" → 245)
        try {
            float v = Float.parseFloat(voltage);
            int progress = Math.min(330, (int) (v * 100));
            pbVoltage.setProgress(progress);
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public void onLedStatusReceived(String status) {
        ledIsOn = "ON".equalsIgnoreCase(status);
        updateLedUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        zeroMQService.disconnect();
    }
}