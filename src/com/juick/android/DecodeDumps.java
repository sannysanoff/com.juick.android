package com.juick.android;

import android.annotation.TargetApi;
import android.os.Build;
import com.juickadvanced.lang.URLDecoder;

import java.io.*;
import java.nio.charset.Charset;
import java.util.zip.GZIPInputStream;

/**
 * Created by san on 8/5/14.
 */
public class DecodeDumps {
    public static void main(String[] args) throws IOException {
        File file = new File("/home/san/Work/jui/android/com.juick.android/proto/20140808");
        String[] files = file.list();
        for (String s : files) {
            if (s.endsWith(".dump")) {
                File thisFile = new File(file, s);
                byte[] bytes = new byte[(int)thisFile.length()];
                FileInputStream fis = new FileInputStream(thisFile);
                int rd = fis.read(bytes, 0, bytes.length);
                fis.close();
                String content = new String(bytes, 0);
                int ix1 = content.indexOf("\n\n");
                int ix2 = content.indexOf("HTTP/1.1 200");
                if (ix2 == -1 || ix1 == 1) continue;
                String headers = content.substring(0, ix1+2);
                String requestBody = content.substring(ix1+2, ix2);
                if (headers.contains(": chunked")) {
                    requestBody = decodeRequestBodyChunked(requestBody);
                }
                if (headers.contains("Content-Encoding: gzip")) {
                    requestBody = decodeRequestBodyGzip(requestBody);
                }
                if (requestBody.contains("=%")) {
                    requestBody = URLDecoder.decode(requestBody);
                }
                File file1 = new File(file, s + ".decoded");
                PrintStream ps = new PrintStream(file1);
                ps.println(headers);
                ps.println(requestBody);
                ps.println();
                ps.println(content.substring(ix2));
                ps.close();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private static String decodeRequestBodyGzip(String requestBody) throws IOException {
        try {
            byte[] bytes = requestBody.getBytes(Charset.forName("ISO-8859-1"));
            GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes));
            StringBuilder sb = new StringBuilder();
            while(true) {
                int cha = gzipInputStream.read();
                if (cha == -1) break;
                sb.append((char)cha);
            }
            return sb.toString();
        } catch (IOException e) {
            return requestBody;
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private static String decodeRequestBodyChunked(String requestBody) {
        ByteArrayInputStream bais = new ByteArrayInputStream(requestBody.getBytes(Charset.forName("ISO-8859-1")));
        StringBuilder rv = new StringBuilder();
        while(true) {
            StringBuilder len = new StringBuilder();
            while(true) {
                int x = bais.read();
                if (x == '\n') break;
                len.append((char) x);
            }
            int leni = Integer.parseInt(len.toString().replace("\r", " ").trim(), 16);
            if (leni == 0) break;
            for(int i=0; i<leni; i++) {
                rv.append((char)bais.read());
            }
            bais.read();    // \r
            bais.read();    // \n
        }
        return rv.toString();
    }

}
