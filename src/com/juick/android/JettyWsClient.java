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

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class JettyWsClient {

    Socket sock;
    InputStream is;
    OutputStream os;
    WsClientListener listener = null;
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

    public interface WsClientListener {

        public void onWebSocketTextFrame(String data) throws IOException;
    }

    public JettyWsClient() {
    }

    public void setListener(WsClientListener listener) {
        this.listener = listener;
    }

    public boolean connect(String host, int port, String location, String headers) {
        try {

            MySSLSocketFactory sf = new MySSLSocketFactory(null);
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            sock = sf.createSocket();
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
            is.close();
            os.close();
            sock.close();
        } catch (Exception e) {
            System.err.println(e);
        }
    }


    static byte[][] allowedSignatures = new byte[][] {
            /*ja.ip.rt.ru */ new byte[] {-114,53,80,-7,114,-98,6,-50,-121,-46,127,58,-64,-4,12,-125,-19,38,-31,112,-10,56,-32,-101,113,67,-84,-9,60,-70,73,73,31,-46,123,98,-23,-118,45,-37,-7,-90,-117,111,123,-66,-15,-59,-69,-108,-16,-26,18,-71,112,33,2,88,39,62,30,-40,110,119,106,-90,95,91,127,-32,-54,107,37,-118,-8,-27,57,-85,7,36,-12,81,36,103,31,29,64,31,37,77,-48,-114,-24,-101,73,98,-39,-22,41,102,58,-40,-11,-115,-26,59,110,-44,49,58,80,-128,59,106,-93,56,104,-65,25,40,-59,-21,-48,65,-78,91,-107,68,-12,-72,37,-28,-53,54,73,28,-35,-68,34,-91,32,124,57,-76,61,111,-12,-56,3,48,-68,111,121,127,28,-50,50,67,-21,15,-116,-12,7,57,31,38,29,79,45,-96,104,-1,-62,17,122,99,10,35,-60,83,38,-103,57,-81,-77,-44,52,-65,45,93,110,-59,24,2,119,5,95,-44,-51,-71,106,122,37,-14,-89,42,92,-64,71,-57,14,-44,57,-83,-30,-34,87,-119,106,41,110,-57,51,-4,32,-27,86,62,113,-35,40,108,90,-55,91,90,-89,8,-45,63,-123,-59,-108,9,100,13,68,87,-112,-19,84,-71,-17,-2,-74,40},
            /* localhost */ new byte[] {106,51,41,24,68,-57,-20,13,-94,88,-18,-30,-127,-128,-41,56,98,-26,-49,-95,69,127,-72,-24,68,-85,46,-8,112,44,-76,51,79,25,-55,34,63,79,-85,-49,-22,44,90,-108,59,-63,96,-33,18,71,94,-58,-25,-102,30,21,-6,78,-122,-48,-7,-36,-25,-16,79,-72,-40,-17,72,-55,-122,71,2,-44,-81,-29,108,14,34,78,-3,110,9,81,21,84,-90,67,26,68,-124,-42,112,-103,-114,-15,40,-125,-60,-12,-100,102,20,87,-13,-97,71,-103,-41,-84,106,-78,-16,-35,-35,27,-37,-108,70,3,-101,78,-90,17,91,97,3,70,-68,-72,-94,19,54,117,-28,102,78,-42,13,-23,-118,12,-55,-33,-32,107,19,-32,80,-78,-42,107,94,28,-95,-123,-31,-50,58,-120,103,-100,-75,-95,-124,-121,57,-39,-40,54,-12,47,6,-106,-5,3,37,-88,-22,120,-27,117,122,114,-114,-39,-36,-89,-104,107,-32,89,7,-45,-15,117,54,-32,123,-124,46,-76,96,-35,68,28,-98,63,94,16,-43,-18,44,-120,-8,-57,52,33,66,91,21,41,-120,-29,-51,10,82,-89,65,-72,-122,103,67,8,-77,56,95,43,-128,-4,113,-23,-2,125,-68,28,-38,-7,-124,40,87,103,33,87,20,45}
    };

    class MySSLSocketFactory extends SSLSocketFactory {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        public MySSLSocketFactory(KeyStore truststore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
            super(truststore);

            TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    boolean verified = false;
                    if (chain.length == 1) {
                        byte[] thisSignature = chain[0].getSignature();
                        for (byte[] allowedSignature : allowedSignatures) {
                            if (thisSignature.length == allowedSignature.length) {
                                verified = true;
                                for (int i = 0; i < allowedSignature.length; i++) {
                                    if (thisSignature[i] != allowedSignature[i]) {
                                        verified = false;
                                        break;
                                    }
                                }
                                if (verified)
                                    break;
                            }
                        }
                    }
                    if (!verified)
                        throw new CertificateException("Invalid HTTPS certificate for Juick Advanced server. Please update Juick Advanced client, this may fix it.");
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };

            sslContext.init(null, new TrustManager[]{tm}, null);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
            return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
        }

        @Override
        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }
    }

}
