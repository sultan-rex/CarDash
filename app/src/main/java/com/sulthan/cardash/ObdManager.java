package com.sulthan.cardash;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Talks to a Bluetooth ELM327 OBD-II adapter on a background thread and
 * publishes readings to listeners on the main thread.
 */
public final class ObdManager {
    private static final String TAG = "ObdManager";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final long RETRY_DELAY_MS = 5000;

    public enum State { DISCONNECTED, CONNECTING, CONNECTED }

    public interface Listener {
        void onObdData(ObdData data);
        void onObdState(State state, String message);
    }

    public interface DtcCallback {
        /** codes is null when the request failed; message explains why. */
        void onResult(List<String> codes, String message);
    }

    private static final ObdManager INSTANCE = new ObdManager();
    public static ObdManager get() { return INSTANCE; }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Job> jobs = new ConcurrentLinkedQueue<>();
    private final ObdData data = new ObdData();

    private volatile State state = State.DISCONNECTED;
    private volatile String stateMessage = "OBD off";
    private volatile Thread worker;
    private volatile boolean running;
    private volatile BluetoothSocket socket;

    private ObdManager() {}

    public State state() { return state; }
    public String stateMessage() { return stateMessage; }
    public synchronized ObdData snapshot() { return data.copy(); }

    public void addListener(Listener l) {
        listeners.add(l);
        l.onObdState(state, stateMessage);
        l.onObdData(snapshot());
    }

    public void removeListener(Listener l) { listeners.remove(l); }

    public synchronized void start(String address) {
        stop();
        running = true;
        Thread t = new Thread(() -> runAdapter(address), "obd");
        worker = t;
        t.start();
    }

    public synchronized void stop() {
        running = false;
        closeSocket();
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        jobs.clear();
        synchronized (this) { clear(data); }
        setState(State.DISCONNECTED, "OBD off");
        publish();
    }

    public void readDtcs(DtcCallback cb) { enqueue(new Job(false, cb)); }
    public void clearDtcs(DtcCallback cb) { enqueue(new Job(true, cb)); }

    private void enqueue(Job job) {
        if (state != State.CONNECTED) {
            main.post(() -> job.cb.onResult(null, "Adapter not connected"));
            return;
        }
        jobs.add(job);
    }

    // ------------------------------------------------------------- adapter

    private void runAdapter(String address) {
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        while (alive()) {
            if (bt == null) {
                setState(State.DISCONNECTED, "No Bluetooth");
                return;
            }
            if (!bt.isEnabled()) {
                setState(State.CONNECTING, "Turning on Bluetooth…");
                try { bt.enable(); } catch (SecurityException ignored) {}
                if (!sleep(3000)) return;
                continue;
            }
            try {
                setState(State.CONNECTING, "Connecting…");
                BluetoothDevice dev = bt.getRemoteDevice(address);
                bt.cancelDiscovery();
                socket = connect(dev);
                Elm elm = new Elm(socket.getInputStream(), socket.getOutputStream());
                initAdapter(elm);
                setState(State.CONNECTED, "Connected");
                pollLoop(elm);
            } catch (IOException | SecurityException e) {
                Log.w(TAG, "OBD connection failed", e);
                closeSocket();
                synchronized (this) { clear(data); }
                publish();
                if (!alive()) return;
                setState(State.DISCONNECTED, "Adapter not found, retrying");
                if (!sleep(RETRY_DELAY_MS)) return;
            }
        }
    }

    private static BluetoothSocket connect(BluetoothDevice dev) throws IOException {
        try {
            BluetoothSocket s = dev.createRfcommSocketToServiceRecord(SPP_UUID);
            s.connect();
            return s;
        } catch (IOException first) {
            // Many cheap ELM327 clones only accept a raw connection on channel 1.
            try {
                Method m = dev.getClass().getMethod("createRfcommSocket", int.class);
                BluetoothSocket s = (BluetoothSocket) m.invoke(dev, 1);
                s.connect();
                return s;
            } catch (Exception e) {
                throw first;
            }
        }
    }

    private void initAdapter(Elm elm) throws IOException {
        elm.send("ATZ", 3000);   // reset
        elm.send("ATE0", 1000);  // no echo
        elm.send("ATL0", 1000);  // no line feeds
        elm.send("ATS0", 1000);  // no spaces
        elm.send("ATH0", 1000);  // no headers
        elm.send("ATSP0", 1000); // auto-detect protocol
        elm.send("0100", 10000); // forces the protocol search
    }

    /** PIDs read every cycle (fast) and in rotation (slow). */
    private static final String[] FAST = {"0C", "0D"};
    private static final String[] SLOW = {"05", "11", "04", "0F", "RV"};

    private void pollLoop(Elm elm) throws IOException {
        Map<String, Integer> misses = new HashMap<>();
        int slowIdx = 0;
        while (alive()) {
            for (String pid : FAST) readPid(elm, pid, misses);
            readPid(elm, SLOW[slowIdx], misses);
            slowIdx = (slowIdx + 1) % SLOW.length;
            publish();

            Job job;
            while ((job = jobs.poll()) != null) runJob(elm, job);
            if (!sleep(50)) return;
        }
    }

    private void readPid(Elm elm, String pid, Map<String, Integer> misses) throws IOException {
        Integer m = misses.get(pid);
        if (m != null && m >= 3) return; // car doesn't support this PID

        if (pid.equals("RV")) {
            String r = elm.send("ATRV", 1000);
            try {
                double v = Double.parseDouble(r.replaceAll("[^0-9.]", ""));
                synchronized (this) { data.voltage = v; }
            } catch (NumberFormatException e) {
                misses.put(pid, m == null ? 1 : m + 1);
            }
            return;
        }

        int[] b = parse(elm.send("01" + pid, 1500), "41" + pid);
        if (b == null || b.length == 0) {
            misses.put(pid, m == null ? 1 : m + 1);
            return;
        }
        misses.remove(pid);
        synchronized (this) {
            switch (pid) {
                case "0C": if (b.length >= 2) data.rpm = (b[0] * 256 + b[1]) / 4.0; break;
                case "0D": data.speedKmh = b[0]; break;
                case "05": data.coolantC = b[0] - 40; break;
                case "11": data.throttlePct = b[0] * 100 / 255.0; break;
                case "04": data.loadPct = b[0] * 100 / 255.0; break;
                case "0F": data.intakeC = b[0] - 40; break;
            }
        }
    }

    private void runJob(Elm elm, Job job) throws IOException {
        if (job.clear) {
            String r = elm.send("04", 5000);
            boolean ok = r.contains("44");
            main.post(() -> job.cb.onResult(ok ? new ArrayList<>() : null,
                    ok ? "Codes cleared" : "The car refused to clear codes (" + r + ")"));
            return;
        }
        String proto = elm.send("ATDPN", 1000).replace("A", "").trim();
        boolean can = proto.matches("[6-9]");
        String r = elm.send("03", 5000);
        List<String> codes = decodeDtcs(r, can);
        main.post(() -> job.cb.onResult(codes, codes == null ? "No response (" + r + ")" : null));
    }

    // ------------------------------------------------------------- parsing

    /** Returns the data bytes following {@code header} (e.g. "410C"), or null. */
    static int[] parse(String resp, String header) {
        if (isError(resp)) return null;
        for (String line : resp.split("[\r\n]+")) {
            String l = line.replaceAll("\\s", "").toUpperCase(Locale.US);
            int i = l.indexOf(header);
            if (i < 0) continue;
            String hex = l.substring(i + header.length());
            int n = hex.length() / 2;
            int[] out = new int[n];
            try {
                for (int k = 0; k < n; k++) out[k] = Integer.parseInt(hex.substring(k * 2, k * 2 + 2), 16);
            } catch (NumberFormatException e) {
                return null;
            }
            return out;
        }
        return null;
    }

    /**
     * Decodes a mode 03 response. Older cars send "43" + up to three codes per line;
     * CAN cars send "43" + a code count, split over numbered frames ("0:", "1:").
     */
    static List<String> decodeDtcs(String resp, boolean can) {
        List<String> codes = new ArrayList<>();
        if (resp.contains("NO DATA")) return codes; // no stored codes
        if (isError(resp)) return null;
        boolean sawHeader = false;
        if (can) {
            StringBuilder sb = new StringBuilder();
            for (String line : resp.split("[\r\n]+")) {
                String l = line.replaceAll("\\s", "").toUpperCase(Locale.US);
                if (l.matches("[0-9A-F]{3}")) continue;  // total byte count line
                sb.append(l.replaceFirst("^[0-9A-F]:", ""));
            }
            String s = sb.toString();
            int p = 0;
            while ((p = s.indexOf("43", p)) >= 0 && p + 4 <= s.length()) {
                sawHeader = true;
                int n;
                try { n = Integer.parseInt(s.substring(p + 2, p + 4), 16); }
                catch (NumberFormatException e) { break; }
                int start = p + 4;
                for (int k = 0; k < n && start + 4 * k + 4 <= s.length(); k++) {
                    addDtc(codes, s.substring(start + 4 * k, start + 4 * k + 4));
                }
                p = start + 4 * n;
            }
        } else {
            for (String line : resp.split("[\r\n]+")) {
                String l = line.replaceAll("\\s", "").toUpperCase(Locale.US);
                int i = l.indexOf("43");
                if (i < 0) continue;
                sawHeader = true;
                String hex = l.substring(i + 2);
                for (int k = 0; k + 4 <= hex.length(); k += 4) addDtc(codes, hex.substring(k, k + 4));
            }
        }
        return sawHeader ? codes : null;
    }

    private static void addDtc(List<String> codes, String raw) {
        if (raw.equals("0000")) return;
        String code = formatDtc(raw);
        if (code != null && !codes.contains(code)) codes.add(code);
    }

    private static String formatDtc(String raw) {
        try {
            int first = Integer.parseInt(raw.substring(0, 1), 16);
            char system = "PCBU".charAt(first >> 2);
            return system + Integer.toString(first & 3) + raw.substring(1);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isError(String r) {
        return r.isEmpty() || r.contains("NO DATA") || r.contains("ERROR") || r.contains("UNABLE")
                || r.contains("STOPPED") || r.contains("?") || r.contains("BUS");
    }

    // ------------------------------------------------------------- helpers

    private static void clear(ObdData d) {
        d.speedKmh = d.rpm = d.coolantC = d.throttlePct = d.loadPct = d.intakeC = d.voltage = Double.NaN;
    }

    /** True only for the current worker; an old worker that hasn't exited yet stays quiet. */
    private boolean alive() {
        return running && Thread.currentThread() == worker;
    }

    private void setState(State s, String msg) {
        if (Thread.currentThread() != Looper.getMainLooper().getThread() && !alive()) return;
        state = s;
        stateMessage = msg;
        main.post(() -> { for (Listener l : listeners) l.onObdState(s, msg); });
    }

    private void publish() {
        if (Thread.currentThread() != Looper.getMainLooper().getThread() && !alive()) return;
        ObdData snap = snapshot();
        main.post(() -> { for (Listener l : listeners) l.onObdData(snap); });
    }

    private void closeSocket() {
        BluetoothSocket s = socket;
        socket = null;
        if (s != null) try { s.close(); } catch (IOException ignored) {}
    }

    private boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return alive();
        } catch (InterruptedException e) {
            return false;
        }
    }

    private static final class Job {
        final boolean clear;
        final DtcCallback cb;
        Job(boolean clear, DtcCallback cb) { this.clear = clear; this.cb = cb; }
    }

    /** Minimal ELM327 command/response channel. Responses end with the '>' prompt. */
    private static final class Elm {
        private final InputStream in;
        private final OutputStream out;

        Elm(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        String send(String cmd, long timeoutMs) throws IOException {
            while (in.available() > 0) in.read(); // drop stale bytes
            out.write((cmd + "\r").getBytes("US-ASCII"));
            out.flush();
            StringBuilder sb = new StringBuilder();
            long deadline = SystemClock.elapsedRealtime() + timeoutMs;
            while (SystemClock.elapsedRealtime() < deadline) {
                if (in.available() > 0) {
                    int c = in.read();
                    if (c < 0) throw new IOException("Stream closed");
                    if (c == '>') break;
                    sb.append((char) c);
                } else {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        throw new IOException("Interrupted");
                    }
                }
            }
            return sb.toString().replace("SEARCHING...", "").replace('\r', '\n').trim();
        }
    }
}
