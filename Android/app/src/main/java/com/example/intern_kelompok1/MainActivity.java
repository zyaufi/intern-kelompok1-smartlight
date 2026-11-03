package com.example.intern_kelompok1;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements ZeroMQService.Listener {


    private TextView statusKoneksi, teganganSTM, statusLed, server;
    private TextView kecerahan, mode, warning;
    private Button btnConnect, btnOn, btnOff;
    private Switch switchMode;
    private SeekBar ngaturCerah;

    private ZeroMQService zeroMQService;
    private SharedPreferences prefs;
    private boolean ledIsOn = false;
    private boolean isManualMode = false;
    private int brightnessLevel = 50;

    private static final String PREF_NAME = "SmartLight";
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
        statusKoneksi = findViewById(R.id.statusKoneksi);
        server = findViewById(R.id.server);
        teganganSTM = findViewById(R.id.teganganSTM);
        statusLed = findViewById(R.id.statusLed);

        btnConnect = findViewById(R.id.btnConnect);
        btnOn = findViewById(R.id.btnOn);
        btnOff = findViewById(R.id.btnOff);

        switchMode = findViewById(R.id.switchMode);
        ngaturCerah = findViewById(R.id.ngaturCerah);
        kecerahan = findViewById(R.id.kecerahan);
        mode = findViewById(R.id.mode);
        warning = findViewById(R.id.warning);
    }

    private void setupListeners() {
        btnConnect.setOnClickListener(v -> showIpDialog());
        btnOn.setOnClickListener(v -> {
            if (!zeroMQService.isConnected()) {
                Toast.makeText(this, "Belum terhubung ke server", Toast.LENGTH_SHORT).show();
                return;
            }
            zeroMQService.sendCommand("ON");
            if (isManualMode) {
                zeroMQService.sendCommand("BRIGHT:" + brightnessLevel);
            }
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

            if (isChecked) {
                ngaturCerah.setAlpha(1f);
                mode.setText("MANUAL");
                mode.setTextColor(ContextCompat.getColor(this, R.color.oranye));
                warning.setText("Potensio diabaikan — kontrol via slider");
                warning.setTextColor(ContextCompat.getColor(this, R.color.oranye));
                zeroMQService.sendCommand("MODE:MANUAL");
            } else {
                ngaturCerah.setEnabled(true); //  HARUS TRUE biar bisa digeser oleh kode JSON
                mode.setText("AUTO (Potensio)");
                mode.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
                warning.setText("Aktifkan switch untuk kontrol manual");
                warning.setTextColor(ContextCompat.getColor(this, R.color.text_gray));
                zeroMQService.sendCommand("MODE:AUTO");
                ngaturCerah.setAlpha(0.4f);
            }
        });

        
        ngaturCerah.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                kecerahan.setText(progress + "%");

                // Hanya simpan brightnessLevel jika yang menggeser adalah USER (bukan kode Python)
                if (fromUser) {
                    brightnessLevel = progress;
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (zeroMQService.isConnected() && isManualMode && ledIsOn) {
                    zeroMQService.sendCommand("BRIGHT:" + brightnessLevel);
                }
            }
        });
    }

    private void restoreSavedServer() {
        String savedIp = prefs.getString(KEY_IP, "192.168.1.10");
        int savedPort = prefs.getInt(KEY_PORT, 5555);
        server.setText("Server: " + savedIp + ":" + savedPort);
        ngaturCerah.setAlpha(0.4f);
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
            server.setText("Server: " + ip + ":" + port);

            updateConnectionUI(false, "MENGHUBUNGKAN...");
            zeroMQService.connect(ip, port);
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private void updateConnectionUI(boolean connected, String text) {
        if (connected) {
            statusKoneksi.setText(" TERHUBUNG");
            statusKoneksi.setTextColor(ContextCompat.getColor(this, R.color.hijau));
        } else {
            statusKoneksi.setText(text != null ? text : "TERPUTUS");
            statusKoneksi.setTextColor(ContextCompat.getColor(this, R.color.jingga));
        }
    }

    private void updateLedUI() {
        if (ledIsOn) {
            statusLed.setText("LED: ON");
            statusLed.setTextColor(ContextCompat.getColor(this, R.color.hijau));
        } else {
            statusLed.setText("LED: OFF");
            statusLed.setTextColor(ContextCompat.getColor(this, R.color.text_gray)); // Ini yang diganti
        }
    }

    // ===== CALLBACK DARI ZeroMQService =====
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
    public void onVoltageReceived(String dataJson) {
        try {
            JSONObject json = new JSONObject(dataJson);
            String voltage = json.getString("v");
            int pwmPercent = json.getInt("p"); // Nilai 0-100

            teganganSTM.setText(voltage + " V");


            if (!isManualMode) {

                ngaturCerah.setProgress(pwmPercent);
                kecerahan.setText(pwmPercent + "%");

            }

        } catch (Exception e) {
            // misal python ga ngirim Json setidaknya tegangan tetep kebaca meskipun slider ga gerak
            teganganSTM.setText(dataJson);
        }
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