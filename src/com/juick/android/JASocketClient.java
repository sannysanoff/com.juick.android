/*
 * Juick
 * Copyright (C) 2008-2012, Ugnich Anton
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.juick.android;

import android.net.ConnectivityManager;
import org.apache.http.util.ByteArrayBuffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class JASocketClient {

    Socket sock;
    InputStream is;
    OutputStream os;
    JASocketClientListener listener = null;
    public boolean shuttingDown = false;
    long lastSuccessfulActivity = 0;
    private final String name;

    public boolean send(String str) {
        if (os == null) return false;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            byte[] bytes = str.getBytes("utf-8");
            baos.write(bytes);
            os.write(baos.toByteArray());
            os.write('\n');
            os.flush();
            markActivity();
            return true;
        } catch (IOException e) {
            log("WS send:" + e.toString());
            disconnect();   // will iterate loop
            return false;
        }
    }

    // some successful activity happened on the socket
    private void markActivity() {
        long oldActivity = lastSuccessfulActivity;
        lastSuccessfulActivity = System.currentTimeMillis();
        if (oldActivity != 0) {
            long period = lastSuccessfulActivity - oldActivity;
            ConnectivityChangeReceiver.recordSuccessfulIdlePeriod(JuickAdvancedApplication.instance, period);
        }
    }

    public JASocketClient(String name) {
        this.name = name;
    }

    public void setListener(JASocketClientListener listener) {
        this.listener = listener;
    }

    public boolean connect(String host, int port) {
        try {
            log("JASocketClient:"+name+": new");
            sock = new Socket();
            sock.setSoTimeout(60000);   // 1 minute timeout; to check missing pongs and drop connection
            sock.connect(new InetSocketAddress(host, port));
            is = sock.getInputStream();
            os = sock.getOutputStream();
            log("connected");
            return true;
        } catch (Throwable e) {
            log("connect:"+e.toString());
            System.err.println(e);
            //e.printStackTrace();
            return false;
        }
    }

    public boolean isConnected() {
        return sock != null;
    }

    public void readLoop() {
        long l = System.currentTimeMillis();
        String cause = "??";
        try {
            int b;
            //StringBuilder buf = new StringBuilder();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final InputStream stream = is;
            if (stream != null) {
                while (true) {
                    try {
                        b = stream.read();
                        if (b == -1) {
                            cause = "short read";
                            break;
                        }
                    } catch (SocketTimeoutException e) {
                        if (listener != null) {
                            if (!listener.onNoDataFromSocket()) {
                                cause = "no pong reply withing interval";
                                break;
                            }
                        }
                        continue;
                    }
                    if (shuttingDown) {
                        cause = "shut down client";
                        break;
                    }
                    markActivity();
                    if (b == '\n') {
                        if (listener != null) {
                            listener.onWebSocketTextFrame(new String(baos.toByteArray(), "utf-8"));
                            baos.reset();
                        }
                    } else {
                        baos.write(b);
                    }
                }
            }
        } catch (Exception e) {
            cause = e.toString();
        } finally {
            l = System.currentTimeMillis() - l;
            log("DISCONNECT readLoop: "+l+" msec worked, cause="+cause);
            disconnect();
        }
    }

    private void log(String s) {
        final String logString = "JASocketClient:" + name + ":" + s;
        XMPPService.log(logString);
        JuickAdvancedApplication.addToGlobalLog(logString, null);
    }

    public void disconnect() {
        try {
            if (is != null) is.close();
            if (os != null) os.close();
            if (sock != null) sock.close();
            log("JASocketClient:"+name+": close()");
        } catch (Exception e) {
            System.err.println(e);
        }
    }


}
