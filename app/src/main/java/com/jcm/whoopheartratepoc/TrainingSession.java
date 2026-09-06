package com.jcm.whoopheartratepoc;

import org.json.*;
import java.util.UUID;

/** Deterministic session clock. All times passed in are monotonic milliseconds. */
final class TrainingSession {
    final WorkoutPlan plan;
    final int[][] zones;
    String id = UUID.randomUUID().toString(), address;
    long started = System.currentTimeMillis(), elapsed, stageElapsed, target, measured, weightedHr;
    final long[] zoneMillis = new long[5];
    final long[] stageMillis, stageTarget;
    int stage, bpm, peak;
    boolean paused = true, waiting = true, done;
    String outcome = "active";
    long lastTick, lastHr, lostAt;
    boolean autoResume = true;

    TrainingSession(WorkoutPlan plan, int[][] zones, String address, int stage) {
        this.plan=plan;this.zones=zones;this.address=address;
        this.stage=Math.max(0,Math.min(stage,plan.phases.length-1));
        stageMillis=new long[plan.phases.length];stageTarget=new long[plan.phases.length];
    }
    void tick(long now) {
        long delta=lastTick==0?0:Math.max(0,now-lastTick);
        long from=lastTick; lastTick=now;
        if(done || paused) return;
        // Only count time covered by a recent reading, never an entire delayed callback gap.
        long covered=Math.max(0,Math.min(now,lastHr+6000)-from);
        advance(Math.min(delta,covered));
        if(!done && now-lastHr>6000) {lose(lastHr+6000);lastTick=now;}
    }
    private void advance(long duration) {
        while(duration>0 && !done) {
            long take=Math.min(duration,plan.phases[stage].seconds*1000L-stageElapsed);
            elapsed+=take;stageElapsed+=take;stageMillis[stage]+=take;
            if(bpm>0) {
                measured+=take;weightedHr+=bpm*take;peak=Math.max(peak,bpm);
                int[] range=zones[plan.phases[stage].zone-1];
                if(bpm>=range[0] && bpm<=range[1]) {target+=take;stageTarget[stage]+=take;}
                for(int i=0;i<5;i++)if(bpm>=zones[i][0] && bpm<=zones[i][1]) {zoneMillis[i]+=take;break;}
            }
            duration-=take;
            if(stageElapsed>=plan.phases[stage].seconds*1000L) {
                stage++;stageElapsed=0;
                if(stage==plan.phases.length) {done=true;paused=true;outcome="completed";}
            }
        }
    }
    void sample(int hr,long now) {
        if(hr<=0 || done)return;
        tick(now);if(done)return;
        bpm=hr;lastHr=now;
        if(waiting) {
            waiting=false;
            if(autoResume && (lostAt==0 || now-lostAt<=30000)) paused=false;
            autoResume=false;lastTick=now;
        }
    }
    void lose(long now) {
        if(waiting || done)return;
        autoResume=!paused;paused=true;waiting=true;bpm=0;lostAt=now;lastTick=now;
    }
    boolean toggle(long now) {
        tick(now);if(done)return false;
        if(waiting && autoResume){autoResume=false;paused=true;return true;}
        if(paused) {if(waiting || bpm<=0 || now-lastHr>6000)return false;paused=false;}
        else paused=true;
        autoResume=false;lastTick=now;return true;
    }
    void jump(int index,long now) {
        if(index<0 || index>=plan.phases.length || done)return;
        tick(now);if(done)return;stage=index;stageElapsed=0;lastTick=now;
    }
    void finish(long now) {tick(now);if(!done){done=true;paused=true;outcome="stopped";}}
    long remaining() {return done?0:Math.max(0,plan.phases[stage].seconds*1000L-stageElapsed);}
    int average() {return measured==0?0:(int)(weightedHr/measured);}
    JSONObject json() throws Exception {
        JSONObject j=new JSONObject().put("version",1).put("id",id).put("plan",WorkoutJson.encode(plan))
            .put("address",address).put("started",started).put("elapsed",elapsed).put("stageElapsed",stageElapsed)
            .put("target",target).put("measured",measured).put("weightedHr",weightedHr).put("peak",peak)
            .put("stage",stage).put("done",done).put("outcome",outcome);
        JSONArray z=new JSONArray(), actual=new JSONArray(), perStage=new JSONArray(), hit=new JSONArray();
        for(int i=0;i<5;i++){z.put(new JSONArray().put(zones[i][0]).put(zones[i][1]));actual.put(zoneMillis[i]);}
        for(int i=0;i<stageMillis.length;i++){perStage.put(stageMillis[i]);hit.put(stageTarget[i]);}
        return j.put("zones",z).put("zoneMillis",actual).put("stageMillis",perStage).put("stageTarget",hit);
    }
    static TrainingSession restore(String raw) throws Exception {
        JSONObject j=new JSONObject(raw);if(j.getInt("version")!=1)throw new Exception("Unknown session version");
        WorkoutPlan plan=WorkoutJson.parse(j.getString("plan"));int[][] zones=new int[5][2];
        for(int i=0;i<5;i++){JSONArray z=j.getJSONArray("zones").getJSONArray(i);zones[i][0]=z.getInt(0);zones[i][1]=z.getInt(1);}
        TrainingSession s=new TrainingSession(plan,zones,j.getString("address"),0);
        s.id=j.getString("id");s.started=j.getLong("started");s.elapsed=j.getLong("elapsed");s.stageElapsed=j.getLong("stageElapsed");
        s.target=j.getLong("target");s.measured=j.getLong("measured");s.weightedHr=j.getLong("weightedHr");s.peak=j.getInt("peak");
        s.stage=j.getInt("stage");s.done=j.getBoolean("done");s.outcome=j.getString("outcome");
        if(s.stage<0 || s.stage>plan.phases.length || (!s.done && s.stage==plan.phases.length))throw new Exception("Invalid stage");
        if(s.elapsed<0 || s.stageElapsed<0 || s.target<0 || s.measured<0 || s.weightedHr<0
                || s.target>s.elapsed || s.measured>s.elapsed
                || (!s.done && s.stageElapsed>=plan.phases[s.stage].seconds*1000L))throw new Exception("Invalid session time");
        for(int i=0;i<5;i++)if(zones[i][0]<20 || zones[i][1]>250 || zones[i][0]>zones[i][1]
                || (i>0 && zones[i][0]<=zones[i-1][1]))throw new Exception("Invalid zones");
        for(int i=0;i<5;i++)s.zoneMillis[i]=j.getJSONArray("zoneMillis").getLong(i);
        for(int i=0;i<s.stageMillis.length;i++){s.stageMillis[i]=j.getJSONArray("stageMillis").getLong(i);s.stageTarget[i]=j.getJSONArray("stageTarget").getLong(i);}
        // Never replay wall-clock downtime or automatically resume after process death/reboot.
        s.autoResume=false;s.paused=true;s.waiting=true;return s;
    }
}
