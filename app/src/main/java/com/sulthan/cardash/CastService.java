package com.sulthan.cardash;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes the tablet show up as a "CarDash" DLNA/UPnP media renderer, so a phone app that can cast
 * to DLNA devices (VLC, BubbleUPnP, the Samsung gallery) can send a video to the tablet. The video
 * itself plays in {@link CastPlayerActivity}. YouTube and Netflix cast only to Chromecast, so they
 * will not list it.
 */
public class CastService extends Service {
    static final String PREF_ENABLED = "cast_receiver";
    private static final int HTTP_PORT = 49152;
    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final String TYPE_DEVICE = "urn:schemas-upnp-org:device:MediaRenderer:1";
    private static final String TYPE_AVT = "urn:schemas-upnp-org:service:AVTransport:1";
    private static final String TYPE_RCS = "urn:schemas-upnp-org:service:RenderingControl:1";
    private static final String TYPE_CM = "urn:schemas-upnp-org:service:ConnectionManager:1";
    private static final String SINK = "http-get:*:video/mp4:*,http-get:*:video/x-matroska:*,"
            + "http-get:*:video/mpeg:*,http-get:*:video/3gpp:*,http-get:*:video/webm:*,"
            + "http-get:*:video/x-msvideo:*,http-get:*:audio/mpeg:*";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running;
    private String uuid;
    private MulticastSocket ssdp;
    private ServerSocket http;
    private WifiManager.MulticastLock lock;
    private String currentUri = "";
    private String currentMeta = "";

    private final Runnable alive = new Runnable() {
        @Override public void run() {
            if (!running) return;
            pool.execute(new Runnable() { @Override public void run() { announce("ssdp:alive"); } });
            ui.postDelayed(this, 30000);
        }
    };

    /** Starts or stops the receiver to match the setting. */
    static void sync(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        Intent i = new Intent(ctx, CastService.class);
        if (sp.getBoolean(PREF_ENABLED, false) && !BatterySaver.isSaving(ctx)) ctx.startService(i);
        else ctx.stopService(i);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) return START_STICKY;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.getBoolean(PREF_ENABLED, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        uuid = sp.getString("cast_uuid", null);
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            sp.edit().putString("cast_uuid", uuid).apply();
        }
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            lock = wm.createMulticastLock("cardash-cast");
            lock.acquire();
        }
        running = true;
        pool.execute(new Runnable() { @Override public void run() { serveHttp(); } });
        pool.execute(new Runnable() { @Override public void run() { serveSsdp(); } });
        ui.post(alive);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        ui.removeCallbacks(alive);
        pool.execute(new Runnable() { @Override public void run() { announce("ssdp:byebye"); } });
        try { if (ssdp != null) ssdp.close(); } catch (RuntimeException ignored) { }
        try { if (http != null) http.close(); } catch (IOException ignored) { }
        if (lock != null && lock.isHeld()) lock.release();
        pool.shutdown();
        super.onDestroy();
    }

    // ---- SSDP discovery ----

    private String[] targets() {
        return new String[] {"upnp:rootdevice", "uuid:" + uuid, TYPE_DEVICE, TYPE_AVT, TYPE_RCS, TYPE_CM};
    }

    private String usn(String nt) {
        return nt.startsWith("uuid:") ? nt : "uuid:" + uuid + "::" + nt;
    }

    private void serveSsdp() {
        try {
            ssdp = new MulticastSocket(SSDP_PORT);
            ssdp.setReuseAddress(true);
            InetAddress group = InetAddress.getByName(SSDP_ADDR);
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
                NetworkInterface ni = e.nextElement();
                try {
                    if (ni.isUp() && ni.supportsMulticast() && !ni.isLoopback()) {
                        ssdp.joinGroup(new InetSocketAddress(group, SSDP_PORT), ni);
                    }
                } catch (IOException ignored) { }
            }
            announce("ssdp:alive");
            byte[] buf = new byte[2048];
            while (running) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                ssdp.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                if (!msg.startsWith("M-SEARCH")) continue;
                String st = header(msg, "ST");
                for (String nt : targets()) {
                    if ("ssdp:all".equals(st) || nt.equals(st)) reply(p, nt);
                }
            }
        } catch (IOException ignored) {
            // Closed on shutdown, or no multicast on this network.
        }
    }

    private void reply(DatagramPacket query, String nt) {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(query.getAddress(), query.getPort());
            String msg = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nEXT:\r\n"
                    + "LOCATION: http://" + s.getLocalAddress().getHostAddress() + ":" + HTTP_PORT + "/desc.xml\r\n"
                    + "SERVER: Android/5.1 UPnP/1.0 CarDash/1\r\nST: " + nt + "\r\nUSN: " + usn(nt) + "\r\n\r\n";
            byte[] b = msg.getBytes(StandardCharsets.UTF_8);
            s.send(new DatagramPacket(b, b.length));
        } catch (IOException ignored) { }
    }

    private void announce(String sub) {
        InetAddress group;
        try { group = InetAddress.getByName(SSDP_ADDR); } catch (IOException e) { return; }
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
                NetworkInterface ni = e.nextElement();
                if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) continue;
                for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements();) {
                    InetAddress addr = a.nextElement();
                    if (!(addr instanceof Inet4Address)) continue;
                    try (MulticastSocket s = new MulticastSocket(new InetSocketAddress(addr, 0))) {
                        s.setNetworkInterface(ni);
                        for (String nt : targets()) {
                            String msg = "NOTIFY * HTTP/1.1\r\nHOST: " + SSDP_ADDR + ":" + SSDP_PORT + "\r\n"
                                    + "CACHE-CONTROL: max-age=1800\r\n"
                                    + "LOCATION: http://" + addr.getHostAddress() + ":" + HTTP_PORT + "/desc.xml\r\n"
                                    + "NT: " + nt + "\r\nNTS: " + sub + "\r\nSERVER: Android/5.1 UPnP/1.0 CarDash/1\r\n"
                                    + "USN: " + usn(nt) + "\r\n\r\n";
                            byte[] b = msg.getBytes(StandardCharsets.UTF_8);
                            s.send(new DatagramPacket(b, b.length, group, SSDP_PORT));
                        }
                    } catch (IOException ignored) { }
                }
            }
        } catch (IOException ignored) { }
    }

    private static String header(String msg, String name) {
        for (String line : msg.split("\r\n")) {
            int c = line.indexOf(':');
            if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase(name)) return line.substring(c + 1).trim();
        }
        return "";
    }

    // ---- HTTP: descriptions and SOAP control ----

    private void serveHttp() {
        try {
            http = new ServerSocket();
            http.setReuseAddress(true);
            http.bind(new InetSocketAddress(HTTP_PORT));
            while (running) {
                final Socket s = http.accept();
                pool.execute(new Runnable() { @Override public void run() { handle(s); } });
            }
        } catch (IOException ignored) {
            // Closed on shutdown, or the port is taken.
        }
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(10000);
            InputStream in = sock.getInputStream();
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int state = 0, b;
            while (state < 4 && (b = in.read()) >= 0) {
                head.write(b);
                state = (b == '\r' && (state == 0 || state == 2)) || (b == '\n' && (state == 1 || state == 3)) ? state + 1
                        : (b == '\r' ? 1 : 0);
            }
            String req = head.toString("UTF-8");
            String[] first = req.split("\r\n")[0].split(" ");
            if (first.length < 2) return;
            String method = first[0], path = first[1];
            int len = 0;
            try { len = Integer.parseInt(header(req, "Content-Length")); } catch (NumberFormatException ignored) { }
            byte[] body = new byte[len];
            for (int off = 0; off < len;) {
                int n = in.read(body, off, len - off);
                if (n < 0) break;
                off += n;
            }
            OutputStream out = sock.getOutputStream();
            if (method.equals("GET")) {
                String xml = description(path);
                if (xml == null) respond(out, 404, "text/plain", "", null);
                else respond(out, 200, "text/xml; charset=\"utf-8\"", xml, null);
            } else if (method.equals("POST")) {
                String action = header(req, "SOAPACTION").replace("\"", "");
                int hash = action.indexOf('#');
                String service = hash < 0 ? "" : action.substring(0, hash);
                String name = hash < 0 ? action : action.substring(hash + 1);
                String result = control(service, name, new String(body, StandardCharsets.UTF_8));
                if (result == null) respond(out, 500, "text/xml; charset=\"utf-8\"", soapFault(), null);
                else respond(out, 200, "text/xml; charset=\"utf-8\"", soapEnvelope(service, name, result), null);
            } else if (method.equals("SUBSCRIBE")) {
                respond(out, 200, "text/plain", "", "SID: uuid:" + UUID.randomUUID() + "\r\nTIMEOUT: Second-1800\r\n");
            } else {
                respond(out, 200, "text/plain", "", null);
            }
        } catch (IOException ignored) { }
    }

    private static void respond(OutputStream out, int code, String type, String body, String extra) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : code == 404 ? " Not Found" : " Error") + "\r\n"
                + "Content-Type: " + type + "\r\nContent-Length: " + b.length + "\r\n"
                + (extra != null ? extra : "") + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }

    private String description(String path) {
        switch (path) {
            case "/desc.xml": return deviceXml();
            case "/avt.xml": return scpd(AVT_ACTIONS, AVT_VARS);
            case "/rcs.xml": return scpd(RCS_ACTIONS, RCS_VARS);
            case "/cm.xml": return scpd(CM_ACTIONS, CM_VARS);
            default: return null;
        }
    }

    private String deviceXml() {
        return "<?xml version=\"1.0\"?><root xmlns=\"urn:schemas-upnp-org:device-1-0\">"
                + "<specVersion><major>1</major><minor>0</minor></specVersion><device>"
                + "<deviceType>" + TYPE_DEVICE + "</deviceType><friendlyName>CarDash</friendlyName>"
                + "<manufacturer>CarDash</manufacturer><modelName>CarDash tablet</modelName>"
                + "<UDN>uuid:" + uuid + "</UDN><serviceList>"
                + svc(TYPE_AVT, "AVTransport", "avt")
                + svc(TYPE_RCS, "RenderingControl", "rcs")
                + svc(TYPE_CM, "ConnectionManager", "cm")
                + "</serviceList></device></root>";
    }

    private static String svc(String type, String id, String key) {
        return "<service><serviceType>" + type + "</serviceType><serviceId>urn:upnp-org:serviceId:" + id
                + "</serviceId><SCPDURL>/" + key + ".xml</SCPDURL><controlURL>/ctl/" + key
                + "</controlURL><eventSubURL>/evt/" + key + "</eventSubURL></service>";
    }

    // Each action: name, then pairs of "in|out:ArgName:StateVariable".
    private static final String[][] AVT_ACTIONS = {
        {"SetAVTransportURI", "in:InstanceID:A_ARG_TYPE_InstanceID", "in:CurrentURI:AVTransportURI",
            "in:CurrentURIMetaData:AVTransportURIMetaData"},
        {"Play", "in:InstanceID:A_ARG_TYPE_InstanceID", "in:Speed:TransportPlaySpeed"},
        {"Pause", "in:InstanceID:A_ARG_TYPE_InstanceID"},
        {"Stop", "in:InstanceID:A_ARG_TYPE_InstanceID"},
        {"Seek", "in:InstanceID:A_ARG_TYPE_InstanceID", "in:Unit:A_ARG_TYPE_SeekMode", "in:Target:A_ARG_TYPE_SeekTarget"},
        {"GetTransportInfo", "in:InstanceID:A_ARG_TYPE_InstanceID", "out:CurrentTransportState:TransportState",
            "out:CurrentTransportStatus:TransportStatus", "out:CurrentSpeed:TransportPlaySpeed"},
        {"GetPositionInfo", "in:InstanceID:A_ARG_TYPE_InstanceID", "out:Track:CurrentTrack",
            "out:TrackDuration:CurrentTrackDuration", "out:TrackMetaData:AVTransportURIMetaData",
            "out:TrackURI:AVTransportURI", "out:RelTime:RelativeTimePosition", "out:AbsTime:AbsoluteTimePosition"},
        {"GetMediaInfo", "in:InstanceID:A_ARG_TYPE_InstanceID", "out:NrTracks:CurrentTrack",
            "out:MediaDuration:CurrentTrackDuration", "out:CurrentURI:AVTransportURI",
            "out:CurrentURIMetaData:AVTransportURIMetaData"},
    };
    private static final String[][] AVT_VARS = {
        {"A_ARG_TYPE_InstanceID", "ui4"}, {"AVTransportURI", "string"}, {"AVTransportURIMetaData", "string"},
        {"TransportState", "string"}, {"TransportStatus", "string"}, {"TransportPlaySpeed", "string"},
        {"A_ARG_TYPE_SeekMode", "string"}, {"A_ARG_TYPE_SeekTarget", "string"}, {"CurrentTrackDuration", "string"},
        {"RelativeTimePosition", "string"}, {"AbsoluteTimePosition", "string"}, {"CurrentTrack", "ui4"},
    };
    private static final String[][] RCS_ACTIONS = {
        {"GetVolume", "in:InstanceID:A_ARG_TYPE_InstanceID", "in:Channel:A_ARG_TYPE_Channel", "out:CurrentVolume:Volume"},
        {"SetVolume", "in:InstanceID:A_ARG_TYPE_InstanceID", "in:Channel:A_ARG_TYPE_Channel", "in:DesiredVolume:Volume"},
        {"GetMute", "in:InstanceID:A_ARG_TYPE_InstanceID", "in:Channel:A_ARG_TYPE_Channel", "out:CurrentMute:Mute"},
        {"SetMute", "in:InstanceID:A_ARG_TYPE_InstanceID", "in:Channel:A_ARG_TYPE_Channel", "in:DesiredMute:Mute"},
    };
    private static final String[][] RCS_VARS = {
        {"A_ARG_TYPE_InstanceID", "ui4"}, {"A_ARG_TYPE_Channel", "string"}, {"Volume", "ui2"}, {"Mute", "boolean"},
    };
    private static final String[][] CM_ACTIONS = {
        {"GetProtocolInfo", "out:Source:SourceProtocolInfo", "out:Sink:SinkProtocolInfo"},
        {"GetCurrentConnectionIDs", "out:ConnectionIDs:CurrentConnectionIDs"},
    };
    private static final String[][] CM_VARS = {
        {"SourceProtocolInfo", "string"}, {"SinkProtocolInfo", "string"}, {"CurrentConnectionIDs", "string"},
    };

    private static String scpd(String[][] actions, String[][] vars) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">"
                + "<specVersion><major>1</major><minor>0</minor></specVersion><actionList>");
        for (String[] a : actions) {
            sb.append("<action><name>").append(a[0]).append("</name><argumentList>");
            for (int i = 1; i < a.length; i++) {
                String[] p = a[i].split(":");
                sb.append("<argument><name>").append(p[1]).append("</name><direction>").append(p[0])
                        .append("</direction><relatedStateVariable>").append(p[2])
                        .append("</relatedStateVariable></argument>");
            }
            sb.append("</argumentList></action>");
        }
        sb.append("</actionList><serviceStateTable>");
        for (String[] v : vars) {
            sb.append("<stateVariable sendEvents=\"no\"><name>").append(v[0]).append("</name><dataType>")
                    .append(v[1]).append("</dataType></stateVariable>");
        }
        return sb.append("</serviceStateTable></scpd>").toString();
    }

    // ---- SOAP actions ----

    private static String arg(String body, String name) {
        Matcher m = Pattern.compile("<" + name + "[^>]*>(.*?)</" + name + ">", Pattern.DOTALL).matcher(body);
        return m.find() ? unescape(m.group(1)) : "";
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String out(String name, String value) {
        return "<" + name + ">" + esc(value) + "</" + name + ">";
    }

    private static String clock(int ms) {
        int s = Math.max(0, ms) / 1000;
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }

    private static int millis(String t) {
        String[] p = t.split(":");
        if (p.length != 3) return 0;
        try {
            return (int) ((Integer.parseInt(p[0]) * 3600 + Integer.parseInt(p[1]) * 60
                    + Double.parseDouble(p[2])) * 1000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Runs one action and returns the output arguments, or null for an unknown action. */
    private String control(String service, String name, String body) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        switch (name) {
            case "SetAVTransportURI":
                currentUri = arg(body, "CurrentURI");
                currentMeta = arg(body, "CurrentURIMetaData");
                CastPlayerActivity.command(this, "load", currentUri, 0);
                return "";
            case "Play":
                CastPlayerActivity.command(this, "play", currentUri, 0);
                return "";
            case "Pause": CastPlayerActivity.command(this, "pause", currentUri, 0); return "";
            case "Stop": CastPlayerActivity.command(this, "stop", currentUri, 0); return "";
            case "Seek":
                CastPlayerActivity.command(this, "seek", currentUri, millis(arg(body, "Target")));
                return "";
            case "GetTransportInfo":
                return out("CurrentTransportState", CastPlayerActivity.state) + out("CurrentTransportStatus", "OK")
                        + out("CurrentSpeed", "1");
            case "GetPositionInfo":
                return out("Track", "1") + out("TrackDuration", clock(CastPlayerActivity.durMs))
                        + out("TrackMetaData", currentMeta) + out("TrackURI", currentUri)
                        + out("RelTime", clock(CastPlayerActivity.posMs)) + out("AbsTime", clock(CastPlayerActivity.posMs))
                        + out("RelCount", "2147483647") + out("AbsCount", "2147483647");
            case "GetMediaInfo":
                return out("NrTracks", "1") + out("MediaDuration", clock(CastPlayerActivity.durMs))
                        + out("CurrentURI", currentUri) + out("CurrentURIMetaData", currentMeta)
                        + out("NextURI", "NOT_IMPLEMENTED") + out("NextURIMetaData", "")
                        + out("PlayMedium", "NETWORK") + out("RecordMedium", "NOT_IMPLEMENTED")
                        + out("WriteStatus", "NOT_IMPLEMENTED");
            case "GetVolume": {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                return out("CurrentVolume", String.valueOf(max == 0 ? 0 : am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max));
            }
            case "SetVolume": {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                try {
                    int pct = Integer.parseInt(arg(body, "DesiredVolume").trim());
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(pct * max / 100f), 0);
                } catch (NumberFormatException ignored) { }
                return "";
            }
            case "GetMute": return out("CurrentMute", "0");
            case "SetMute": return "";
            case "GetProtocolInfo": return out("Source", "") + out("Sink", SINK);
            case "GetCurrentConnectionIDs": return out("ConnectionIDs", "0");
            default: return null;
        }
    }

    private static String soapEnvelope(String service, String name, String result) {
        return "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:" + name + "Response xmlns:u=\""
                + service + "\">" + result + "</u:" + name + "Response></s:Body></s:Envelope>";
    }

    private static String soapFault() {
        return "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail>"
                + "<UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\"><errorCode>401</errorCode>"
                + "<errorDescription>Invalid Action</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>";
    }
}
