package com.jcm.whoopheartratepoc;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.os.*;
import android.speech.tts.TextToSpeech;
import java.util.*;

/** Owns active-session Bluetooth, monotonic clock and coaching independently of the screen. */
public final class WorkoutService extends Service {
    static WorkoutService instance;
    static TrainingSession latest;
    static String status="";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothAdapter adapter;
    private TextToSpeech speech;
    private boolean speechReady, ended, saved, initialized;
    private final SharedPreferences.OnSharedPreferenceChangeListener audioListener=(p,key)->{
        if(AudioSettings.muted(this) && speech!=null)speech.stop();
    };
    private PowerManager.WakeLock wake;
    private long lastSave, lastNotice, lastCue, directionSince, nextConnect, connectAt, serviceStarted;
    private int attempts, direction, announcedStage=-1;
    private static final UUID HR=uuid("180d"), MEASUREMENT=uuid("2a37"), CCC=uuid("2902");
    private static UUID uuid(String s){return UUID.fromString("0000"+s+"-0000-1000-8000-00805f9b34fb");}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();instance=this;serviceStarted=SystemClock.elapsedRealtime();
        AudioSettings.prefs(this).registerOnSharedPreferenceChangeListener(audioListener);
        BluetoothManager manager=getSystemService(BluetoothManager.class);adapter=manager==null?null:manager.getAdapter();
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("training","Active training",NotificationManager.IMPORTANCE_LOW));
        speech=new TextToSpeech(this,result->{
            speechReady=result==TextToSpeech.SUCCESS;
            if(speechReady && speech!=null){
                int lang=speech.setLanguage(Locale.US);
                speechReady=lang!=TextToSpeech.LANG_MISSING_DATA && lang!=TextToSpeech.LANG_NOT_SUPPORTED;
                speech.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            }
        });
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        try {
            Notification n=notification();
            if(Build.VERSION.SDK_INT>=29)startForeground(21,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            else startForeground(21,n);
            if(!initialized){
                String raw=intent==null?null:intent.getStringExtra("session");
                latest=raw==null?SessionStore.recover(this):TrainingSession.restore(raw);
                if(latest==null || latest.done){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
                latest.autoResume=intent!=null && intent.getBooleanExtra("new",false);
                initialized=true;
                latest.lastTick=SystemClock.elapsedRealtime();latest.lostAt=latest.autoResume?0:latest.lastTick;
                status="Connecting sensor…";SessionStore.checkpoint(this,latest);
                wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Tempo:Training");
                wake.acquire(26*60*60*1000L);
                handler.post(ticker);
            }
            String action=intent==null?null:intent.getAction();
            if("toggle".equals(action))toggle();
            if("next".equals(action))jump(latest.stage+1);
            if("stop".equals(action))finish();
            return START_NOT_STICKY; // Explicit Resume from a checkpoint after process death.
        }catch(Exception e){status="Training could not start. Check Bluetooth permission and retry.";stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
    }
    void toggle(){
        if(latest==null || latest.done)return;
        if(latest.toggle(SystemClock.elapsedRealtime()))say(latest.paused?"Workout paused":"Workout resumed");
        else say("Waiting for a live sensor reading");
        persist();updateNotification();
    }
    void jump(int index){
        if(latest==null || latest.done)return;
        latest.jump(index,SystemClock.elapsedRealtime());announcedStage=-1;persist();updateNotification();
    }
    void finish(){if(latest!=null){latest.finish(SystemClock.elapsedRealtime());complete();}}
    void retry(){
        if(latest!=null && !latest.done){long now=SystemClock.elapsedRealtime();latest.tick(now);latest.lose(now);persist();}
        closeLink();nextConnect=0;attempts=0;
    }
    private final Runnable ticker=new Runnable(){public void run(){
        if(ended || latest==null)return;
        long now=SystemClock.elapsedRealtime();
        boolean waiting=latest.waiting;
        latest.tick(now);
        if(latest.done){complete();return;}
        if(!waiting && latest.waiting){say("Sensor signal lost. Workout paused.");closeLink();nextConnect=now+2000;}
        if(gatt!=null && ((latest.waiting && now-connectAt>15000) || (!latest.waiting && now-latest.lastHr>6000)))lost("No live signal. Reconnecting…");
        if(gatt==null && now>=nextConnect)connect();
        coach(now);
        if(now-lastSave>=5000){persist();lastSave=now;}
        if(now-lastNotice>=2000){updateNotification();lastNotice=now;}
        if(now-serviceStarted>=25*60*60*1000L){finish();return;}
        handler.postDelayed(this,250);
    }};
    private void coach(long now){
        if(latest.paused || latest.waiting){direction=0;return;}
        if(announcedStage!=latest.stage){
            if(!speechReady)return;
            WorkoutPlan.Phase p=latest.plan.phases[latest.stage];int[] r=latest.zones[p.zone-1];
            if(AudioSettings.stages(this))say(p.name+". Zone "+p.zone+". Target "+r[0]+" to "+r[1]+" beats per minute.");
            announcedStage=latest.stage;lastCue=now;direction=0;return;
        }
        if(!AudioSettings.zones(this))return;
        int[] r=latest.zones[latest.plan.phases[latest.stage].zone-1];
        // Small boundary tolerance + sustained deviation + cooldown prevents chatter.
        int next=latest.bpm<r[0]-2?-1:latest.bpm>r[1]+2?1:0;
        if(next!=direction){direction=next;directionSince=now;}
        int seconds=AudioSettings.interval(this);
        if(direction!=0 && now-directionSince>=8000 && now-lastCue>=seconds*1000L){
            say(direction>0?"Above target. Ease off gently.":"Below target. Increase your effort gradually if comfortable.");lastCue=now;
        }
    }
    private void say(String text){if(!AudioSettings.muted(this) && speechReady && speech!=null)speech.speak(text,TextToSpeech.QUEUE_FLUSH,null,"tempo-coach");}
    private void persist(){try{SessionStore.checkpoint(this,latest);}catch(Exception e){status="Session could not be saved. Keep Tempo open.";}}
    private void complete(){
        if(ended)return;
        try{SessionStore.finish(this,latest);saved=true;}
        catch(Exception e){persist();status="History save failed. Reopen Tempo to retry.";}
        ended=true;closeLink();handler.removeCallbacks(ticker);
        latest.bpm=0;latest.waiting=true;
        if(saved)status="Session saved · sensor released";
        updateNotification();
        say(latest.outcome.equals("completed")?(saved?"Workout complete. Session saved.":"Workout complete."):"Workout stopped.");
        if(!saved)say("Could not save history. Please reopen Tempo.");
        // Allow the final spoken message to finish before releasing TTS.
        handler.postDelayed(()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();},5000);
    }
    private PendingIntent command(String action,int code){
        return PendingIntent.getService(this,code,new Intent(this,WorkoutService.class).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        TrainingSession s=latest;
        String text=s==null?"Preparing training":s.done?"Session finished":s.waiting?"Signal missing · timer paused":s.paused?"Paused · tap Resume":
            "Zone "+s.plan.phases[s.stage].zone+" · "+s.bpm+" bpm · "+(s.remaining()/60000)+":"+String.format(Locale.US,"%02d",s.remaining()/1000%60)+" left";
        Notification.Builder b=new Notification.Builder(this,"training").setSmallIcon(R.drawable.tempo_mark)
            .setContentTitle(s==null?"Tempo":s.plan.name).setContentText(text).setContentIntent(open)
            .setOnlyAlertOnce(true).setOngoing(s==null||!s.done).setVisibility(Notification.VISIBILITY_PRIVATE);
        if(s!=null && !s.done){
            b.addAction(new Notification.Action.Builder(null,s.paused && !(s.waiting && s.autoResume)?"Resume":"Pause",command("toggle",1)).build());
            if(s.stage+1<s.plan.phases.length)b.addAction(new Notification.Action.Builder(null,"Next",command("next",2)).build());
            b.addAction(new Notification.Action.Builder(null,"Stop & save",command("stop",3)).build());
        }
        return b.build();
    }
    private void updateNotification(){getSystemService(NotificationManager.class).notify(21,notification());}
    private void connect(){
        connectAt=SystemClock.elapsedRealtime();nextConnect=connectAt+30000;
        try{
            if(adapter==null || !adapter.isEnabled()){status="Turn on Bluetooth. Session paused.";return;}
            status="Reconnecting sensor…";
            gatt=adapter.getRemoteDevice(latest.address).connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE);
            if(gatt==null)lost("Connection unavailable. Retrying…");
        }catch(SecurityException|IllegalArgumentException e){lost("Check Bluetooth permission in Tempo. Session paused.");}
    }
    private void lost(String message){
        if(ended || latest==null)return;
        long now=SystemClock.elapsedRealtime();latest.tick(now);boolean wasWaiting=latest.waiting;latest.lose(now);
        if(!wasWaiting)say("Sensor disconnected. Workout paused.");
        status=message;closeLink();nextConnect=now+Math.min(30000,2000L<<Math.min(attempts++,4));persist();
    }
    private void closeLink(){BluetoothGatt old=gatt;gatt=null;if(old!=null){try{old.disconnect();old.close();}catch(SecurityException ignored){}}}
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt link,int code,int state){handler.post(()->{
            if(link!=gatt || ended)return;
            if(code!=0 || state==BluetoothProfile.STATE_DISCONNECTED)lost("Sensor disconnected. Reconnecting…");
            else if(state==BluetoothProfile.STATE_CONNECTED){try{if(!link.discoverServices())lost("Discovery failed. Retrying…");}catch(SecurityException e){lost("Bluetooth permission needed");}}
        });}
        @Override public void onServicesDiscovered(BluetoothGatt link,int code){handler.post(()->{
            if(link!=gatt || ended)return;
            try{
                BluetoothGattService service=code==0?link.getService(HR):null;
                BluetoothGattCharacteristic hr=service==null?null:service.getCharacteristic(MEASUREMENT);
                BluetoothGattDescriptor ccc=hr==null?null:hr.getDescriptor(CCC);
                if(ccc==null || !link.setCharacteristicNotification(hr,true)){lost("Enable Heart Rate Broadcast on sensor");return;}
                byte[] enable=(hr.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0?BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE:BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                boolean ok;if(Build.VERSION.SDK_INT>=33)ok=link.writeDescriptor(ccc,enable)==BluetoothStatusCodes.SUCCESS;else{ccc.setValue(enable);ok=link.writeDescriptor(ccc);}
                if(!ok)lost("Subscription failed. Retrying…");
            }catch(SecurityException e){lost("Bluetooth permission needed");}
        });}
        @Override public void onDescriptorWrite(BluetoothGatt link,BluetoothGattDescriptor d,int code){handler.post(()->{if(link==gatt && code!=0)lost("Subscription rejected. Retrying…");});}
        @Override public void onCharacteristicChanged(BluetoothGatt link,BluetoothGattCharacteristic c,byte[] bytes){receive(link,c,bytes);}
        @Override public void onCharacteristicChanged(BluetoothGatt link,BluetoothGattCharacteristic c){receive(link,c,c.getValue());}
    };
    private void receive(BluetoothGatt link,BluetoothGattCharacteristic c,byte[] value){
        if(!MEASUREMENT.equals(c.getUuid())||value==null)return;byte[] bytes=value.clone();
        handler.post(()->{
            if(link!=gatt || ended)return;int hr=HeartRatePacket.parse(bytes);if(hr<=0)return;
            boolean wasWaiting=latest.waiting;latest.sample(hr,SystemClock.elapsedRealtime());attempts=0;
            status="Sensor connected · live "+hr+" bpm";
            if(wasWaiting)say(latest.paused?"Sensor connected. Tap Resume when ready.":"Sensor connected. Training resumed.");
        });
    }
    @Override public void onDestroy(){
        AudioSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(audioListener);
        if(latest!=null && !latest.done){latest.tick(SystemClock.elapsedRealtime());latest.paused=true;latest.autoResume=false;latest.waiting=true;latest.bpm=0;persist();}
        ended=true;handler.removeCallbacksAndMessages(null);closeLink();
        if(wake!=null && wake.isHeld())wake.release();
        if(speech!=null){speech.stop();speech.shutdown();}
        if(instance==this)instance=null;super.onDestroy();
    }
    @Override protected void dump(java.io.FileDescriptor fd,java.io.PrintWriter out,String[] args){
        TrainingSession s=latest;
        out.println("Tempo training: speechReady="+speechReady+", wakeLock="+(wake!=null && wake.isHeld()));
        if(s!=null)out.println("stage="+s.stage+", elapsedMs="+s.elapsed+", stageElapsedMs="+s.stageElapsed
            +", paused="+s.paused+", waiting="+s.waiting+", done="+s.done);
    }
}
