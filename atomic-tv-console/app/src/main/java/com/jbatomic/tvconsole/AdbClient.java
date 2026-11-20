/*
 * ATOMIC TV Console — JB Atomic Industries — https://jbatomic.com/
 * © 2026 JB Atomic Industries.
 * Loopback-only authenticated ADB TCP transport; does not bypass Android permissions.
 * Protocol client: com.tananaev:adblib:1.3 (BSD-3-Clause).
 */
package com.jbatomic.tvconsole;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import com.tananaev.adblib.AdbBase64;
import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbCrypto;
import com.tananaev.adblib.AdbStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class AdbClient {
    interface Result { void onResult(boolean ok, String output); }
    private final Context context;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final AdbBase64 encoder=data->Base64.encodeToString(data, Base64.NO_WRAP);
    AdbClient(Context ctx){context=ctx.getApplicationContext();}
    boolean isBusy(){return busy.get();}
    void run(int port, String command, Result callback){
        if(!busy.compareAndSet(false,true)){callback.onResult(false,"Une commande est déjà en cours. / A command is already running.");return;}
        worker.execute(()->{
            String output;
            boolean ok=false;
            try{output=execute(port,command);ok=true;}
            catch(Exception e){String msg=e.getMessage();output=e.getClass().getSimpleName()+": "+(msg==null?"Connection refused / Check ADB TCP 5555 and the RSA permission prompt.":msg);}
            final boolean resultOk=ok;
            final String resultText=output;
            main.post(()->{busy.set(false);callback.onResult(resultOk,resultText);});
        });
    }
    private AdbCrypto loadCrypto() throws Exception {
        File key=new File(context.getFilesDir(),"console-adb-private.key");
        File pub=new File(context.getFilesDir(),"console-adb-public.key");
        if(key.exists()&&pub.exists())return AdbCrypto.loadAdbKeyPair(encoder,key,pub);
        AdbCrypto crypto=AdbCrypto.generateAdbKeyPair(encoder);
        crypto.saveAdbKeyPair(key,pub);
        return crypto;
    }
    private String execute(int port,String shellCommand) throws Exception{
        if(port<1024||port>65535)throw new IllegalArgumentException("Invalid TCP port / Port TCP incorrect.");
        if(shellCommand==null||shellCommand.length()>500||shellCommand.indexOf('\n')>=0||shellCommand.indexOf('\r')>=0)
            throw new IllegalArgumentException("Command must be one line (max 500 characters). / Une seule ligne (500 caractères max).");
        // Only connect to the device itself, never exposing a remote-network ADB client.
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress("127.0.0.1",port),4500);
            socket.setSoTimeout(25000);
            AdbConnection connection=AdbConnection.create(socket,loadCrypto());
            try {
                if(!connection.connect(24000,TimeUnit.MILLISECONDS,false))
                    throw new IOException("ADB: accept the RSA authorization on your TV / Validez la clé RSA sur la TV, puis réessayez.");
                try(AdbStream stream=connection.open("shell:"+shellCommand)){
                    StringBuilder out=new StringBuilder();
                    long deadline=System.currentTimeMillis()+16000;
                    while(!stream.isClosed() && System.currentTimeMillis()<deadline && out.length()<40000){
                        try{
                            byte[] chunk=stream.read();
                            if(chunk!=null)out.append(new String(chunk,StandardCharsets.UTF_8));
                        } catch(IOException end){break;}
                    }
                    if(out.length()>=40000)out.append("\n[Output truncated / Sortie tronquée]");
                    return out.length()==0?"(No output / Aucune sortie)":out.toString();
                }
            }finally{connection.close();}
        }
    }
    void close(){worker.shutdownNow();}
}
