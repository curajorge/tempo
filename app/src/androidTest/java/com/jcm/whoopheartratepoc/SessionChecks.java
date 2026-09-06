package com.jcm.whoopheartratepoc;

import android.content.Context;

/** No radio, speaker, real workouts or Activity state are touched by these tests. */
final class SessionChecks {
    private static int count;
    static int run(Context isolated) throws Exception {
        count=0;
        AudioSettings.prefs(isolated).edit().clear().commit();
        ok(!AudioSettings.muted(isolated) && AudioSettings.stages(isolated) && AudioSettings.zones(isolated),"audio defaults");
        AudioSettings.prefs(isolated).edit().putBoolean("muted",true).putBoolean("stage_audio",false).putInt("cue_seconds",60).commit();
        ok(AudioSettings.muted(isolated) && !AudioSettings.stages(isolated) && AudioSettings.interval(isolated)==60,"audio preferences persist independently");
        AudioSettings.prefs(isolated).edit().putInt("cue_seconds",0).commit();
        ok(AudioSettings.interval(isolated)==45,"invalid audio interval falls back");
        AudioSettings.prefs(isolated).edit().clear().commit();
        SessionStore.prefs(isolated).edit().clear().commit();
        WorkoutPlan p=new WorkoutPlan("Session test",new WorkoutPlan.Phase("Warm",1,10),new WorkoutPlan.Phase("Cruise",2,10));
        int[][] z={{90,110},{111,130},{131,150},{151,170},{171,190}};
        TrainingSession s=new TrainingSession(p,z,"00:00:00:00:00:00",0);
        s.sample(100,1000);s.tick(2000);
        ok(s.elapsed==1000 && s.target==1000 && !s.paused,"live starts/counts target");
        s.toggle(2000);s.tick(5000);ok(s.elapsed==1000,"manual pause freezes time");
        s.lose(5000);s.sample(100,6000);ok(s.paused,"manual pause survives reconnect");
        s.toggle(6000);s.tick(7000);ok(s.elapsed==2000,"explicit resume");
        s.tick(14000);ok(s.waiting && s.paused && s.elapsed==7000,"stale signal caps counted time");
        s.sample(100,16000);ok(!s.paused,"short dropout resumes");
        s.tick(17000);ok(s.elapsed==8000,"missing time excluded");
        s.lose(17000);s.sample(100,48001);ok(s.paused && !s.waiting,"long dropout needs consent");
        s.toggle(48001);s.tick(49001);
        long recorded=s.elapsed;s.jump(1,49001);ok(s.elapsed==recorded && s.stageElapsed==0,"skip preserves totals");
        s.jump(99,49001);ok(s.stage==1,"invalid skip ignored");
        s.sample(120,50001);s.tick(51001);ok(s.stageTarget[1]==1000,"stage target uses selected zone");
        s.sample(120,55001);s.sample(120,59001);
        ok(s.done && s.outcome.equals("completed") && s.stage==2,"automatic completion");
        ok(s.measured==s.elapsed && s.average()>0,"weighted average coverage");
        TrainingSession restored=TrainingSession.restore(s.json().toString());
        ok(restored.elapsed==s.elapsed && restored.id.equals(s.id),"snapshot roundtrip");
        TrainingSession active=new TrainingSession(p,z,"00:00:00:00:00:00",0);
        active.sample(100,1000);active.tick(2000);
        SessionStore.checkpoint(isolated,active);
        TrainingSession recovered=SessionStore.recover(isolated);
        recovered.sample(100,999999);
        ok(recovered.paused && recovered.elapsed==1000,"restore pauses and excludes downtime");
        recovered.toggle(999999);recovered.tick(1000999);ok(recovered.elapsed==2000,"restored session resumes");
        recovered.finish(1000999);ok(recovered.outcome.equals("stopped"),"manual stop recorded");
        SessionStore.finish(isolated,recovered);SessionStore.finish(isolated,recovered);
        ok(SessionStore.history(isolated).length()==1,"idempotent history save");
        ok(SessionStore.recover(isolated)==null,"finished checkpoint cleared");
        SessionStore.finish(isolated,s);ok(SessionStore.history(isolated).length()==2,"independent records");
        SessionStore.delete(isolated,recovered.id);ok(SessionStore.history(isolated).length()==1,"delete only selected record");
        TrainingSession gap=new TrainingSession(new WorkoutPlan("Long",new WorkoutPlan.Phase("Steady",1,300)),z,"00:00:00:00:00:00",0);
        gap.sample(100,1000);gap.sample(100,50000);
        ok(gap.paused && gap.elapsed==6000,"long silent callback gap does not auto-resume");
        gap.toggle(50000);gap.lose(51000);gap.toggle(51000);gap.sample(100,52000);
        ok(gap.paused,"cancel pending auto-resume");
        gap.jump(0,53000);ok(gap.paused && gap.stageElapsed==0,"restart stays paused");
        boolean invalid=false;
        try{TrainingSession.restore(gap.json().put("stageElapsed",-1).toString());}catch(Exception expected){invalid=true;}
        ok(invalid,"corrupt checkpoint rejected");
        long total=0;for(long stageTime:s.stageMillis)total+=stageTime;
        ok(total==s.elapsed,"per-stage totals reconcile");
        SessionStore.prefs(isolated).edit().clear().commit();
        return count;
    }
    static void ok(boolean condition,String message) throws Exception {if(!condition)throw new Exception(message);count++;}
}
