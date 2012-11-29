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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;

import android.content.Context;
import com.juickadvanced.data.juick.JuickMessage;
import com.juick.android.juick.JuickCompatibleURLMessagesSource;
import com.juickadvanced.data.juick.JuickMessageID;
import org.apache.http.util.ByteArrayBuffer;

/**
 *
 * @author Ugnich Anton
 */
public class WsClient implements ThreadFragment.ThreadExternalUpdater {

    static final byte keepAlive[] = {(byte) 0x00, (byte) 0x20, (byte) 0xFF};
    Socket sock;
    InputStream is;
    OutputStream os;
    Listener listener;
    Context ctx;
    JuickMessageID mid;
    boolean terminated;

    @Override
    public void terminate() {
        terminated = true;
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public WsClient(Context context, final JuickMessageID mid) {
        this.ctx = context;
        Thread wsthr = new Thread(new Runnable() {

            public void run() {
                while (!terminated) {
                    if (connect("api.juick.com", 8080, "/replies/" + mid.getMid(), null)) {
                        readLoop();
                    }
                }
            }
        },"Websocket thread: mid="+mid);
        wsthr.start();
    }

    public boolean connect(String host, int port, String location, String headers) {
        try {
            sock = new Socket(host, port);
            is = sock.getInputStream();
            os = sock.getOutputStream();

            String handshake = "GET " + location + " HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Upgrade: WebSocket\r\n" +
                    "Origin: http://juick.com/\r\n" +
                    "Sec-WebSocket-Key1: 9 9 9 9\r\n" +
                    "Sec-WebSocket-Key2: 8 8 8 8 8\r\n" +
                    "Sec-WebSocket-Protocol: sample\r\n";
            if (headers != null) {
                handshake += headers;
            }
            handshake += "\r\n" + "12345678";
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

    public void sendTextFrame(String str) throws IOException {
        int len = str.getBytes().length;
        byte buf[] = new byte[len + 2];
        buf[0] = 0x00;
        System.arraycopy(str.getBytes(), 0, buf, 1, len);
        buf[len + 1] = (byte) 0xFF;
        os.write(buf);
        os.flush();
    }

    public void sendKeepAlive() {
        try {
            os.write(keepAlive);
            os.flush();
        } catch (IOException e) {
            //disconnect();
            }
    }

    public void readLoop() {
        try {
            int b;
            //StringBuilder buf = new StringBuilder();
            ByteArrayBuffer buf = new ByteArrayBuffer(16);
            boolean flagInside = false;
            while ((b = is.read()) != -1) {
                if (b == 0x00 && !flagInside) {
                    //buf = new StringBuilder();
                    buf.clear();
                    flagInside = true;
                } else if (b == 0xFF && flagInside) {
                    if (listener != null) {
                        String incomingData = new String(buf.toByteArray(), "utf-8");
                        JuickCompatibleURLMessagesSource jcus = new JuickCompatibleURLMessagesSource(ctx);
                        final ArrayList<JuickMessage> messages = jcus.parseJSONpure("[" + incomingData + "]");
                        listener.onNewMessages(messages);
                    }
                    flagInside = false;
                } else if (flagInside) {
                    buf.append((char) b);
                }
            }
            System.err.println("DISCONNECTED readLoop");
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public void disconnect() {
        try {
            is.close();
            os.close();
            sock.close();
        } catch (Exception e) {
            System.err.println(e);
        }
    }
}
