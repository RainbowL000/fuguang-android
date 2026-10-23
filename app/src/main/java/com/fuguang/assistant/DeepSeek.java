package com.fuguang.assistant;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class DeepSeek {
    static String ask(String key,String payload) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL("https://api.deepseek.com/chat/completions").openConnection();
        try{
            conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(20000);conn.setReadTimeout(90000);conn.setRequestMethod("POST");conn.setDoOutput(true);
            conn.setRequestProperty("Authorization","Bearer "+key);conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
            byte[] data=payload.getBytes(StandardCharsets.UTF_8);conn.setFixedLengthStreamingMode(data.length);
            try(OutputStream out=conn.getOutputStream()){out.write(data);}
            int status=conn.getResponseCode();InputStream source=status>=400?conn.getErrorStream():conn.getInputStream();
            if(source==null)throw new IOException("服务没有返回内容。");
            try(InputStream in=source;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>2*1024*1024)throw new IOException("回答过长，请缩小问题范围。");out.write(buf,0,n);}
                return Payload.answer(new String(out.toByteArray(),StandardCharsets.UTF_8),status);
            }
        }finally{conn.disconnect();}
    }
}
