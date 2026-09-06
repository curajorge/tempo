package com.jcm.whoopheartratepoc;

import android.app.Instrumentation;
import android.os.Bundle;

public final class ImportChecks extends Instrumentation {
    int passed;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            android.content.Context testContext = new android.content.ContextWrapper(getTargetContext()) {
                @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
                    return super.getSharedPreferences("isolated_check_" + name, mode);
                }
            };
            String valid = "{\"format\":\"tempo-workout\",\"version\":1,\"name\":\"AI test\",\"notes\":\"Easy pace\","
                + "\"stages\":[{\"name\":\"Warm\",\"seconds\":300,\"zone\":1},{\"name\":\"Steady\",\"seconds\":1200,\"zone\":2}]}";
            WorkoutPlan plan = WorkoutJson.parse(valid);
            check(plan.totalSeconds() == 1500 && plan.phases.length == 2, "duration/stages");
            check(WorkoutJson.parse("```json\n" + valid + "\n```").totalSeconds() == 1500, "code block");
            check(WorkoutJson.parse(WorkoutJson.encode(plan)).notes.equals("Easy pace"), "save round trip");
            reject(valid.replace("\"zone\":2", "\"zone\":6"));
            reject(valid.replace("\"seconds\":300", "\"seconds\":0"));
            reject(valid.replace("\"seconds\":300", "\"seconds\":1.5"));
            reject(valid.replace("\"seconds\":300", "\"seconds\":\"300\""));
            reject(valid.replace("\"version\":1", "\"version\":2"));
            reject(valid.replace("\"name\":\"AI test\"", "\"name\":\"\""));
            reject(valid.replace("\"zone\":2", "\"zone\":true"));
            reject(valid + " trailing text");
            reject("");
            reject("{\"format\":\"tempo-workout\",\"version\":1,\"name\":\"Empty\",\"stages\":[]}");
            int[][] zones = {{90,110},{111,130},{131,150},{151,170},{171,190}};
            String prompt = AiWorkouts.promptText("30 minutes steady", zones);
            check(prompt.contains("30 minutes steady") && prompt.contains("111–130 bpm")
                    && prompt.contains("tempo-workout"), "prompt includes goal/zones/schema");
            android.content.SharedPreferences testPrefs = testContext.getSharedPreferences("tempo_workouts", 0);
            check(testPrefs.edit().putString("library", new org.json.JSONArray().put(WorkoutJson.encode(plan)).toString()).commit(), "test data written to disk");
            check(AiWorkouts.load(testContext).size() == 1
                    && AiWorkouts.load(testContext).get(0).totalSeconds() == 1500, "persistent library");
            WorkoutPlan renamed = new WorkoutPlan("Renamed workout", plan.phases);
            renamed.notes = "Updated notes";
            WorkoutStore.change(testContext, plan, renamed);
            check(AiWorkouts.load(testContext).get(0).name.equals("Renamed workout"), "rename persisted");
            check(AiWorkouts.load(testContext).get(0).notes.equals("Updated notes"), "notes persisted");
            WorkoutPlan copy = new WorkoutPlan("Copy", plan.phases);
            WorkoutStore.change(testContext, null, copy);
            check(AiWorkouts.load(testContext).size() == 2, "duplicate saved separately");
            WorkoutStore.change(testContext, renamed, null);
            check(AiWorkouts.load(testContext).size() == 1 && AiWorkouts.load(testContext).get(0).name.equals("Copy"), "delete only selected record");
            boolean staleRejected = false;
            try { WorkoutStore.change(testContext, renamed, null); } catch (Exception expected) { staleRejected = true; }
            check(staleRejected && AiWorkouts.load(testContext).size() == 1, "stale delete leaves other records intact");
            WorkoutPlan edited = new WorkoutPlan("Copy", new WorkoutPlan.Phase("New stage", 3, 60));
            WorkoutStore.change(testContext, copy, edited);
            check(AiWorkouts.load(testContext).get(0).totalSeconds() == 60
                    && AiWorkouts.load(testContext).get(0).phases[0].zone == 3, "stage edit persisted");
            testPrefs.edit().clear().commit(); // Only the isolated_check_ preferences, never the real library.
            passed += SessionChecks.run(testContext);
            result.putString("stream", passed + " import, storage and session checks passed\n");
            finish(-1, result);
        } catch (Throwable e) {
            result.putString("stream", "FAIL after " + passed + ": " + e.toString());
            finish(0, result);
        }
    }
    void reject(String text) throws Exception {
        try { WorkoutJson.parse(text); } catch (Exception expected) { passed++; return; }
        throw new Exception("Accepted invalid input: " + text);
    }
    void check(boolean ok, String name) throws Exception {
        if (!ok) throw new Exception(name); passed++;
    }
}
