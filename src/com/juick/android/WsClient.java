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
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import android.content.Context;
import android.util.Log;
import com.juickadvanced.data.juick.JuickMessage;
import com.juick.android.juick.JuickCompatibleURLMessagesSource;
import com.juickadvanced.data.juick.JuickMessageID;
import org.apache.http.util.ByteArrayBuffer;

/**
 *
 * @author Ugnich Anton
 */
public class WsClient implements ThreadFragment.ThreadExternalUpdater {

    public static int instanceCount;
    {
        instanceCount++;
    }

    static final byte keepAlive[] = {(byte) 0x81, (byte) 0x01, (byte) 0x20};
    static final byte closeConnection[] = {(byte) 0x88, (byte) 0x00};
    Socket sock;
    InputStream is;
    OutputStream os;
    Listener listener;
    Context ctx;
    JuickMessageID mid;
    boolean terminated;
    private final Thread wsthr;
    private int beforePausedCounter;

    @Override
    public void terminate() {
        terminated = true;
        setPaused(false);   // these are various means to terminate socket
        disconnect();
        listener = null;        // these are measures to unreference gui if sockets stuck anyway
        ctx = null;
        Log.w("UgnichWS", "inst="+toString()+": terminated="+terminated);
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void setPaused(boolean paused) {
        beforePausedCounter = paused ? 3 : 0;
    }

    public WsClient(Context context, final JuickMessageID mid) {
        this.ctx = context;
        wsthr = new Thread(new Runnable() {

            public void run() {
                while (!terminated) {
                    if (connect("ws.juick.com", 80, "/" + mid.getMid(), null)) {
                        readLoop();
                    }
                }
            }
        }, "Websocket thread: mid=" + mid);
        wsthr.start();
    }

    public boolean connect(String host, int port, String location, String headers) {
        try {
            sock = new Socket(host, port);
            sock.setSoTimeout(15000);
            is = sock.getInputStream();
            os = sock.getOutputStream();

            String handshake = "GET " + location + " HTTP/1.1\r\n"
                    + "Host: " + host + "\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Origin: http://juick.com/\r\n"
                    + "User-Agent: JuickAndroid\r\n"
                    + "Sec-WebSocket-Key: SomeKey\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Pragma: no-cache\r\n"
                    + "Cache-Control: no-cache\r\n";
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
            int byteCnt = 0;
            boolean bigPacket = false;
            int PacketLength = 0;
            ByteArrayBuffer buf = new ByteArrayBuffer(16);
            boolean flagInside = false;
            while (!terminated) {
                try {
                    b = is.read();
                    if (b == -1) break;
                    if (terminated) break;
                } catch (SocketTimeoutException e) {
                    if (beforePausedCounter-- < 0) {
                        sock.setSoTimeout(3*60*1000);
                    }
                    Log.w("UgnichWS", "inst="+toString()+": read sotimeout, terminated="+terminated);
                    continue;
                }

                if (flagInside) {
                    byteCnt++;
                    if (byteCnt == 1) {
                        if (b < 126) {
                            PacketLength = b + 1;
                            bigPacket = false;
                        } else {
                            bigPacket = true;
                        }
                    } else {
                        if (byteCnt == 2 && bigPacket) {
                            PacketLength = b << 8;
                        }
                        if (byteCnt == 3 && bigPacket) {
                            PacketLength |= b;
                            PacketLength += 3;
                        }

                        if (byteCnt > 3 || !bigPacket) {
                            buf.append((char) b);
                        }
                    }

                    if (byteCnt == PacketLength && listener != null) {
                        if (PacketLength > 2) {
                            if (listener != null) {
                                String incomingData = new String(buf.toByteArray(), "utf-8");
                                JuickCompatibleURLMessagesSource jcus = new JuickCompatibleURLMessagesSource(ctx, "dummy");
                                final ArrayList<JuickMessage> messages = jcus.parseJSONpure("[" + incomingData + "]");
                                listener.onNewMessages(messages);
                            }
                        } else {
                            os.write(keepAlive);
                            os.flush();
                        }
                        flagInside = false;
                    }
                } else if (b == 0x81) {
                    buf.clear();
                    flagInside = true;
                    byteCnt = 0;
                }
            }
        } catch (Exception e) {
            Log.e("UgnichWS", "inst="+toString(), e);

        } finally {
            Log.w("UgnichWS", "inst="+toString()+" DISCONNECTED readLoop");
        }
    }

    public void disconnect() {
        try {
            os.write(closeConnection);
            os.flush();
        } catch (Exception e) {
        }
        try {
            is.close();
            os.close();
            sock.close();
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        instanceCount--;
    }
}
