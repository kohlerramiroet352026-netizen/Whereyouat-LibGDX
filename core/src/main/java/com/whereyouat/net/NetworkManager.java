package com.whereyouat.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.badlogic.gdx.Gdx;

public class NetworkManager {
    public static final int DEFAULT_PORT = 25565;

    public boolean isHost = false;
    public boolean isConnected = false;

    private volatile boolean disconnecting = false;

    private volatile boolean acceptCancelled = false;

    private ServerSocket serverSocket;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenerThread;

    public interface NetworkListener {
        void onConnected();
        void onMessageReceived(String message);
        void onDisconnected();
    }

    private volatile NetworkListener listener;

    public void setListener(NetworkListener listener) {
        this.listener = listener;
    }

    public String startHost() {
        try {
            isHost = true;
            disconnecting = false;
            acceptCancelled = false;

            serverSocket = new ServerSocket(DEFAULT_PORT, 1);

            Thread acceptThread = new Thread(() -> {
                try {
                    safeLog("Network", "Esperando conexion del Jugador 2...");
                    Socket accepted = serverSocket.accept();

                    if (acceptCancelled) {
                        try { accepted.close(); } catch (Exception ignored) {}
                        return;
                    }

                    synchronized (NetworkManager.this) {
                        if (acceptCancelled) {
                            try { accepted.close(); } catch (Exception ignored) {}
                            return;
                        }
                        socket = accepted;
                    }

                    setupStreams();

                    try { serverSocket.close(); } catch (Exception ignored) {}
                } catch (Exception e) {
                    if (!acceptCancelled) {
                        safeLog("Network", "Error en servidor: " + e.getMessage());
                    }
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();

            return generateRoomCode();
        } catch (Exception e) {
            safeLog("Network", "startHost error: " + e.getMessage());
            return null;
        }
    }

    public boolean connectAsClient(String roomCode) {
        try {
            isHost = false;
            disconnecting = false;
            acceptCancelled = false;
            String ip = decodeRoomCode(roomCode);
            if (ip == null || ip.trim().isEmpty()) return false;

            String resolvedIp = ip.trim();

            try {
                String localIp = java.net.InetAddress.getLocalHost().getHostAddress();
                if (resolvedIp.equals(localIp)) {
                    safeLog("Network", "Conexion local detectada: usando 127.0.0.1");
                    resolvedIp = "127.0.0.1";
                }
            } catch (Exception ignored) {}

            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(resolvedIp, DEFAULT_PORT), 5000);
            setupStreams();
            return true;
        } catch (Exception e) {
            safeLog("Network", "Error al conectar al mundo: " + e.getMessage());
            return false;
        }
    }

    private void setupStreams() throws Exception {
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        isConnected = true;

        final NetworkListener l = listener;
        if (l != null) {
            safePost(() -> l.onConnected());
        }

        listenerThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final NetworkListener cur = listener;
                    if (cur != null) {
                        final String msg = line;

                        safePost(() -> cur.onMessageReceived(msg));
                    }
                }
            } catch (Exception ignored) {}

            disconnect();
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void send(String msg) {
        if (out != null && isConnected) {
            out.println(msg);
        }
    }

    public void reopenServer() {
        synchronized (this) {
            disconnecting = false;
            acceptCancelled = false;
        }
        isConnected = false;

        try { if (in     != null) in.close();     } catch (Exception ignored) {}
        try { if (out    != null) out.close();     } catch (Exception ignored) {}
        try { if (socket != null) socket.close();  } catch (Exception ignored) {}
        in     = null;
        out    = null;
        socket = null;

        try { if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close(); } catch (Exception ignored) {}

        try {
            serverSocket = new ServerSocket(DEFAULT_PORT, 1);

            Thread acceptThread = new Thread(() -> {
                try {
                    safeLog("Network", "Host: esperando reconexion del Jugador 2...");
                    Socket accepted = serverSocket.accept();

                    if (acceptCancelled) {
                        try { accepted.close(); } catch (Exception ignored) {}
                        return;
                    }

                    synchronized (NetworkManager.this) {
                        if (acceptCancelled) {
                            try { accepted.close(); } catch (Exception ignored) {}
                            return;
                        }
                        socket = accepted;
                    }

                    setupStreams();

                    try { serverSocket.close(); } catch (Exception ignored) {}
                } catch (Exception e) {
                    if (!acceptCancelled) {
                        safeLog("Network", "Error en servidor (reopen): " + e.getMessage());
                    }
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.setName("net-accept-reopen");
            acceptThread.start();
        } catch (Exception e) {
            safeLog("Network", "reopenServer error: " + e.getMessage());
        }
    }

    public void disconnect() {
        synchronized (this) {
            if (disconnecting) return;
            disconnecting = true;
            acceptCancelled = true;
        }

        isConnected = false;

        final NetworkListener l = listener;
        listener = null;

        try { if (socket       != null) socket.setSoLinger(true, 0); } catch (Exception ignored) {}
        try { if (socket       != null) socket.close();               } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.close();         } catch (Exception ignored) {}
        try { if (in           != null) in.close();                   } catch (Exception ignored) {}
        try { if (out          != null) out.close();                  } catch (Exception ignored) {}

        if (l != null) {
            safePost(() -> l.onDisconnected());
        }
    }

    private String generateRoomCode() {
        try {
            String ip = java.net.InetAddress.getLocalHost().getHostAddress();

            return Base64.getUrlEncoder().withoutPadding().encodeToString(ip.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "LOCAL123";
        }
    }

    private String decodeRoomCode(String code) {
        try {
            String cleanCode = code.trim().replaceAll("[^A-Za-z0-9_=-]", "");
            if (cleanCode.isEmpty()) return null;

            byte[] decoded = Base64.getUrlDecoder().decode(cleanCode);
            String ip = new String(decoded, StandardCharsets.UTF_8).trim();

            if (ip.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
                return ip;
            }

            String directIp = code.trim().replaceAll("[^0-9.]", "");
            return directIp.isEmpty() ? null : directIp;
        } catch (Exception e) {
            String directIp = code.trim().replaceAll("[^0-9.]", "");
            return directIp.isEmpty() ? null : directIp;
        }
    }

    private static void safePost(Runnable r) {
        try {
            if (Gdx.app != null) {
                Gdx.app.postRunnable(r);
            }
        } catch (Throwable ignored) {}
    }

    private static void safeLog(String tag, String msg) {
        try {
            if (Gdx.app != null) {
                Gdx.app.log(tag, msg);
            }
        } catch (Throwable ignored) {}
    }
}
