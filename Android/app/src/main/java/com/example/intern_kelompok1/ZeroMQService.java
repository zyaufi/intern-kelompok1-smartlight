package com.example.intern_kelompok1;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.LinkedBlockingQueue;

public class ZeroMQService {

    public interface Listener {
        void onConnected();
        void onDisconnected(String reason);
        void terimaDataTegangan(String voltage);
        void terimaStatusLed(String status);
    }

    private ZContext context;
    private ZMQ.Socket socket;
    private Thread workerThread;
    private volatile boolean isRunning = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final LinkedBlockingQueue<String> commandQueue = new LinkedBlockingQueue<>();

    public ZeroMQService(Listener listener) {
        this.listener = listener;
    }

    public void connect(String ip, int port) {
        if (isRunning) disconnect();
        isRunning = true;
        workerThread = new Thread(() -> runClient(ip, port));
        workerThread.start();
    }

    private void runClient(String ip, int port) {
        try {
            context = new ZContext();
            socket = context.createSocket(SocketType.REQ);
            socket.setReceiveTimeOut(5000);
            socket.setSendTimeOut(5000);
            socket.setLinger(0);

            Log.d("ZMQ_DEBUG", "Mencoba connect ke tcp://" + ip + ":" + port);
            socket.connect("tcp://" + ip + ":" + port);

            socket.send("TEGANGAN", 0);
            byte[] reply = socket.recv(0);

            if (reply == null) {
                mainHandler.post(() -> listener.onDisconnected("Server tidak merespon"));
                isRunning = false;
                return;
            }

            Log.d("ZMQ_DEBUG", "Berhasil terhubung ke server!");
            mainHandler.post(listener::onConnected);

            while (isRunning) {
                try {
                    String cmd = commandQueue.poll();
                    if (cmd != null) {
                        Log.d("ZMQ_DEBUG", "Mengirim perintah: " + cmd);
                        socket.send(cmd, 0);
                        socket.recv(0);
                    } else {
                        socket.send("TEGANGAN", 0);
                        byte[] data = socket.recv(0);
                        if (data != null) {
                            final String voltage = new String(data).trim();
                            mainHandler.post(() -> listener.terimaDataTegangan(voltage));
                        }
                    }
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    Log.e("ZMQ_ERR", "Error di loop: " + e.getMessage(), e);
                    if (isRunning) {
                        mainHandler.post(() -> listener.onDisconnected("Koneksi terputus di tengah jalan"));
                    }
                    break;
                }
            }

        } catch (Exception e) {
            Log.e("ZMQ_ERR", "Gagal connect: " + e.getMessage(), e);
            final String err = e.getMessage() != null ? e.getMessage() : "Unknown Error";
            mainHandler.post(() -> listener.onDisconnected(err));
        } finally {
            isRunning = false;
        }
    }

    public void sendCommand(String command) {
        if (isRunning) {
            commandQueue.offer(command);
        }
    }

    public void disconnect() {
        isRunning = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        if (socket != null) { socket.close(); socket = null; }
        if (context != null) { context.close(); context = null; }
    }

    public boolean isConnected() {
        return isRunning;
    }
}