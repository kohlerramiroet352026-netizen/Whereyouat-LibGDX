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

    // Flag para garantizar que disconnect() solo se ejecuta una vez,
    // aunque sea llamado desde el hilo principal Y desde el listenerThread simultáneamente.
    private volatile boolean disconnecting = false;

    // Flag para evitar que el thread de accept llame a setupStreams()
    // después de que disconnect() ya fue invocado.
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

    // --- 1. INICIAR COMO HOST (máximo 2 jugadores: host + 1 cliente) ---
    public String startHost() {
        try {
            isHost = true;
            disconnecting = false;
            acceptCancelled = false;
            // backlog=1 garantiza que solo se acepta 1 conexión entrante en la cola
            serverSocket = new ServerSocket(DEFAULT_PORT, 1);

            // Hilo DAEMON en segundo plano esperando que el Jugador 2 se conecte.
            // Daemon = se termina automáticamente cuando la JVM sale, sin bloquearla.
            Thread acceptThread = new Thread(() -> {
                try {
                    safeLog("Network", "Esperando conexion del Jugador 2...");
                    Socket accepted = serverSocket.accept();

                    // Verificar que no se canceló mientras esperábamos
                    if (acceptCancelled) {
                        try { accepted.close(); } catch (Exception ignored) {}
                        return;
                    }

                    // Asignación atómica del socket aceptado
                    synchronized (NetworkManager.this) {
                        if (acceptCancelled) {
                            try { accepted.close(); } catch (Exception ignored) {}
                            return;
                        }
                        socket = accepted;
                    }

                    setupStreams();
                    // Ya aceptamos al único cliente: cerrar el server socket
                    // para rechazar cualquier intento de un tercer jugador.
                    try { serverSocket.close(); } catch (Exception ignored) {}

                } catch (Exception e) {
                    // Si el serverSocket fue cerrado intencionalmente, no loguear como error
                    if (!acceptCancelled) {
                        safeLog("Network", "Error en servidor: " + e.getMessage());
                    }
                }
            });
            acceptThread.setDaemon(true); // ← CRÍTICO: daemon para que la JVM pueda terminar
            acceptThread.start();

            // Retorna el código de sala generado
            return generateRoomCode();
        } catch (Exception e) {
            safeLog("Network", "startHost error: " + e.getMessage());
            return null;
        }
    }

    // --- 2. CONECTARSE COMO CLIENTE ---
    public boolean connectAsClient(String roomCode) {
        try {
            isHost = false;
            disconnecting = false;
            acceptCancelled = false;
            String ip = decodeRoomCode(roomCode);
            if (ip == null || ip.trim().isEmpty()) return false;

            String resolvedIp = ip.trim();

            // Si el código apunta a esta misma máquina (misma IP LAN),
            // usar 127.0.0.1 para evitar que el Firewall de Windows bloquee
            // la conexión entrante sobre la interfaz de red.
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

        // Hilo DAEMON que lee mensajes entrantes continuamente
        listenerThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    final NetworkListener cur = listener;
                    if (cur != null) {
                        final String msg = line;
                        // LibGDX requiere ejecutar acciones visuales en el hilo principal
                        safePost(() -> cur.onMessageReceived(msg));
                    }
                }
            } catch (Exception ignored) {}
            // Cuando el socket se cierra (el otro jugador se fue), llamar disconnect
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

    /**
     * Solo para el HOST: reabre el ServerSocket para que el cliente pueda
     * reconectarse con el mismo código de sala después de haberse desconectado.
     * Resetea todos los flags y streams del cliente anterior, mantiene isHost = true.
     */
    public void reopenServer() {
        // Resetear flags de la conexión anterior
        synchronized (this) {
            disconnecting = false;
            acceptCancelled = false;
        }
        isConnected = false;

        // Limpiar streams y socket del cliente anterior
        try { if (in     != null) in.close();     } catch (Exception ignored) {}
        try { if (out    != null) out.close();     } catch (Exception ignored) {}
        try { if (socket != null) socket.close();  } catch (Exception ignored) {}
        in     = null;
        out    = null;
        socket = null;

        // Cerrar el serverSocket anterior si por alguna razón sigue abierto
        try { if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close(); } catch (Exception ignored) {}

        // Abrir un nuevo ServerSocket en el mismo puerto
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
                    // Cerrar el serverSocket tras aceptar al único cliente
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

    /**
     * Cierra la conexión de forma segura.
     * Garantizado idempotente: aunque sea llamado simultáneamente desde
     * el hilo principal (dispose) y el listenerThread, solo ejecuta el
     * bloque de limpieza una vez gracias al flag 'disconnecting'.
     */
    public void disconnect() {
        // Comparar-y-asignar atómico: solo el primer caller entra al bloque
        synchronized (this) {
            if (disconnecting) return;
            disconnecting = true;
            acceptCancelled = true; // también cancelar el accept si estaba esperando
        }

        isConnected = false;

        // Anular el listener ANTES de cerrar streams para que el listenerThread
        // no pueda llamar onDisconnected() al mismo tiempo que nosotros.
        final NetworkListener l = listener;
        listener = null;

        // Cerrar el socket PRIMERO para que in.close() y out.close() fallen rápido
        // en vez de bloquearse esperando flush() en el GL thread (lo que congela el juego).
        // setSoLinger y close() están en try-catch SEPARADOS: si setSoLinger lanza excepción,
        // el close() igual se ejecuta (antes estaban juntos y close() podía quedar saltado).
        try { if (socket       != null) socket.setSoLinger(true, 0); } catch (Exception ignored) {}
        try { if (socket       != null) socket.close();               } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.close();         } catch (Exception ignored) {}
        try { if (in           != null) in.close();                   } catch (Exception ignored) {}
        try { if (out          != null) out.close();                  } catch (Exception ignored) {}

        // Notificar al juego UNA sola vez desde el hilo principal
        if (l != null) {
            safePost(() -> l.onDisconnected());
        }
    }

    // --- 3. GENERAR Y DECODIFICAR CÓDIGO DE SALA ---
    private String generateRoomCode() {
        try {
            // Obtiene la IP local de la máquina
            String ip = java.net.InetAddress.getLocalHost().getHostAddress();
            // Lo codifica en Base64 para que parezca una clave secreta (ej: MTI3LjAuMC4x)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(ip.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "LOCAL123";
        }
    }

    private String decodeRoomCode(String code) {
        try {
            // Limpiar: solo caracteres válidos en Base64-URL (A-Za-z0-9 _ - =)
            String cleanCode = code.trim().replaceAll("[^A-Za-z0-9_=-]", "");
            if (cleanCode.isEmpty()) return null;

            byte[] decoded = Base64.getUrlDecoder().decode(cleanCode);
            String ip = new String(decoded, StandardCharsets.UTF_8).trim();

            // Validar que el resultado sea una IPv4 con formato correcto (ej: 192.168.0.17)
            // Si hay basura extra en el portapapeles, la IP decodificada no pasará esta validación
            if (ip.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
                return ip;
            }

            // No es una IPv4 válida: intentar usar el código directamente como IP
            String directIp = code.trim().replaceAll("[^0-9.]", "");
            return directIp.isEmpty() ? null : directIp;
        } catch (Exception e) {
            // Si falla el decode Base64, intentar tratarlo como IP directa
            String directIp = code.trim().replaceAll("[^0-9.]", "");
            return directIp.isEmpty() ? null : directIp;
        }
    }

    /**
     * Envuelve Gdx.app.postRunnable() en un try-catch para que nunca crashee
     * si Gdx.app ya fue destruido (ej: la aplicación está cerrando).
     */
    private static void safePost(Runnable r) {
        try {
            if (Gdx.app != null) {
                Gdx.app.postRunnable(r);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Envuelve Gdx.app.log() en un try-catch para que nunca crashee
     * si Gdx.app ya fue destruido.
     */
    private static void safeLog(String tag, String msg) {
        try {
            if (Gdx.app != null) {
                Gdx.app.log(tag, msg);
            }
        } catch (Throwable ignored) {}
    }
}
