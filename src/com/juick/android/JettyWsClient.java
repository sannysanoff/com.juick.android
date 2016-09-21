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

import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.ByteArrayBuffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class JettyWsClient {

    Socket sock;
    InputStream is;
    OutputStream os;
    JASocketClientListener listener = null;
    public boolean shuttingDown = false;

    public boolean send(String str) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            byte[] bytes = str.getBytes("utf-8");
            baos.write(0x81);
            baos.write((byte)bytes.length);
            baos.write(bytes);
            os.write(baos.toByteArray());
            os.flush();
            return true;
        } catch (IOException e) {
            System.out.println("WS send:" +e.toString());
            return false;
        }
    }

    public JettyWsClient() {
    }

    public void setListener(JASocketClientListener listener) {
        this.listener = listener;
    }

    public boolean connect(String host, int port, String location, String headers) {
        try {

            SSLSocketFactory sf = SSLSocketFactory.getSocketFactory();
            HttpParams params = new BasicHttpParams();
            sock = sf.connectSocket(sock, host, port, null, 0, params);


            is = sock.getInputStream();
            os = sock.getOutputStream();

            String handshake = "GET " + location + " HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Upgrade: WebSocket\r\n" +
                    "Sec-WebSocket-Version: 13\r\n" +
                    "Sec-WebSocket-Key: 9 9 9 9\r\n" +
                    "Sec-WebSocket-Protocol: sample\r\n";
            if (headers != null) {
                handshake += headers;
            }
            handshake += "\r\n";
            os.write(handshake.getBytes());
            return true;
        } catch (Exception e) {
            System.err.println(e);
            //e.printStackTrace();
            return false;
        }
    }

    public boolean isConnected() {
        return sock.isConnected();
    }

    public void readLoop() {
        try {
            int b;
            //StringBuilder buf = new StringBuilder();
            ByteArrayBuffer buf = new ByteArrayBuffer(16);
            boolean flagInside = false;
            StringBuilder baad = new StringBuilder();
            int lenToRead = -1;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while ((b = is.read()) != -1) {
                if (b == 0x81) {
                    // 80 = final frame
                    // 1 = text
                    if ((b = is.read()) != -1) {
                        lenToRead = b;
                        baos.reset();
                        continue;
                    } else {
                        break;
                    }
                }
                if (lenToRead != -1) {
                    baos.write(b);
                    lenToRead--;
                    if (lenToRead == 0) {
                        lenToRead = -1;
                        if (listener != null) {
                            listener.onWebSocketTextFrame(new String(baos.toByteArray(), "utf-8"));
                        }
                    }
                }
            }
            System.err.println("DISCONNECTED readLoop");
            disconnect();
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public void disconnect() {
        try {
            if (is != null) is.close();
            if (os != null) os.close();
            if (sock != null) sock.close();
        } catch (Exception e) {
            System.err.println(e);
        }
    }


}
