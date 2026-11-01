package com.logistics.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight, zero-dependency Redis RESP (REdis Serialization Protocol) client.
 * Provides atomic script execution (EVAL) and basic commands with socket timeouts.
 */
public class RedisClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisClient.class);

    private final String host;
    private final int port;
    private final int timeoutMs;
    private Socket socket;
    private BufferedInputStream in;
    private BufferedOutputStream out;

    public RedisClient(String host, int port, int timeoutMs) {
        this.host = (host != null && !host.isBlank()) ? host : "localhost";
        this.port = port > 0 ? port : 6379;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 2000;
    }

    private synchronized void ensureConnected() throws IOException {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            closeQuietly();
            socket = new Socket();
            socket.setSoTimeout(timeoutMs);
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            in = new BufferedInputStream(socket.getInputStream());
            out = new BufferedOutputStream(socket.getOutputStream());
        }
    }

    public synchronized boolean ping() {
        try {
            ensureConnected();
            sendCommand("PING");
            Object response = readResponse();
            return "PONG".equalsIgnoreCase(String.valueOf(response));
        } catch (Exception e) {
            log.debug("Redis ping failed: {}", e.getMessage());
            closeQuietly();
            return false;
        }
    }

    /**
     * Executes an EVAL script atomically on Redis.
     *
     * @param script Lua script
     * @param numKeys number of keys
     * @param args keys and arguments
     * @return parsed RESP response object
     * @throws IOException on connection or protocol errors
     */
    public synchronized Object eval(String script, int numKeys, String... args) throws IOException {
        ensureConnected();
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add("EVAL");
        cmdArgs.add(script);
        cmdArgs.add(String.valueOf(numKeys));
        if (args != null) {
            for (String arg : args) {
                cmdArgs.add(arg != null ? arg : "");
            }
        }
        sendCommand(cmdArgs.toArray(new String[0]));
        return readResponse();
    }

    public synchronized void del(String key) throws IOException {
        ensureConnected();
        sendCommand("DEL", key);
        readResponse();
    }

    public synchronized void flushDb() throws IOException {
        ensureConnected();
        sendCommand("FLUSHDB");
        readResponse();
    }

    private void sendCommand(String... parts) throws IOException {
        try {
            out.write(('*' + String.valueOf(parts.length) + "\r\n").getBytes(StandardCharsets.UTF_8));
            for (String part : parts) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                out.write(('$' + String.valueOf(bytes.length) + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(bytes);
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.flush();
        } catch (IOException e) {
            closeQuietly();
            throw e;
        }
    }

    private Object readResponse() throws IOException {
        try {
            int firstByte = in.read();
            if (firstByte == -1) {
                throw new IOException("Unexpected EOF while reading from Redis server");
            }
            char type = (char) firstByte;
            switch (type) {
                case '+': // Simple string
                    return readLine();
                case '-': // Error
                    String err = readLine();
                    throw new IOException("Redis error: " + err);
                case ':': // Integer
                    return Long.parseLong(readLine());
                case '$': // Bulk string
                    int length = Integer.parseInt(readLine());
                    if (length == -1) return null;
                    byte[] data = new byte[length];
                    int read = 0;
                    while (read < length) {
                        int r = in.read(data, read, length - read);
                        if (r == -1) throw new IOException("Unexpected EOF reading bulk string");
                        read += r;
                    }
                    in.read(); // \r
                    in.read(); // \n
                    return new String(data, StandardCharsets.UTF_8);
                case '*': // Array
                    int count = Integer.parseInt(readLine());
                    if (count == -1) return null;
                    List<Object> list = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        list.add(readResponse());
                    }
                    return list;
                default:
                    throw new IOException("Unknown Redis RESP type: " + type);
            }
        } catch (IOException e) {
            closeQuietly();
            throw e;
        }
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                int next = in.read();
                if (next == '\n') break;
                baos.write(b);
                if (next != -1) baos.write(next);
            } else {
                baos.write(b);
            }
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private void closeQuietly() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {
        } finally {
            in = null;
            out = null;
            socket = null;
        }
    }

    @Override
    public void close() {
        closeQuietly();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}

