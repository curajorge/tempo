package com.jcm.whoopheartratepoc;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

final class WorkoutJson {
    static WorkoutPlan parse(String input) throws Exception {
        if (input == null || input.trim().isEmpty()) throw new Exception("Paste the response from WHOOP AI first.");
        if (input.length() > 65536) throw new Exception("This response is too long. Use at most 100 stages.");
        String source = input.trim();
        if (source.startsWith("```")) {
            int line = source.indexOf('\n');
            if (line < 0 || !source.endsWith("```")) throw new Exception("Copy the entire JSON code block.");
            source = source.substring(line + 1, source.length() - 3).trim();
        }
        JSONObject json;
        try {
            JSONTokener tokener = new JSONTokener(source);
            Object value = tokener.nextValue();
            if (!(value instanceof JSONObject) || tokener.nextClean() != 0) throw new Exception();
            json = (JSONObject) value;
        } catch (Exception e) { throw new Exception("Could not read JSON. Copy only the workout object or its complete code block."); }
        if (!"tempo-workout".equals(json.optString("format"))) throw new Exception("Missing format: tempo-workout. Use Tempo's copied prompt.");
        if (integer(json.opt("version"), "Version", 1, 1) != 1) throw new Exception("Unsupported version.");
        String name = string(json.opt("name"), "Workout name", 120);
        JSONArray stages = json.optJSONArray("stages");
        if (stages == null || stages.length() == 0 || stages.length() > 100)
            throw new Exception("Include 1–100 stages.");
        WorkoutPlan.Phase[] phases = new WorkoutPlan.Phase[stages.length()];
        int total = 0;
        for (int i = 0; i < stages.length(); i++) {
            JSONObject stage = stages.optJSONObject(i);
            String prefix = "Stage " + (i + 1);
            if (stage == null) throw new Exception(prefix + " must be an object.");
            String label = string(stage.opt("name"), prefix + " name", 120);
            int seconds = integer(stage.opt("seconds"), prefix + " seconds", 1, 21600);
            int zone = integer(stage.opt("zone"), prefix + " zone", 1, 5);
            total += seconds;
            if (total > 86400) throw new Exception("Total duration must not exceed 24 hours.");
            phases[i] = new WorkoutPlan.Phase(label, zone, seconds);
        }
        WorkoutPlan plan = new WorkoutPlan(name, phases);
        if (json.has("notes")) {
            Object notes = json.opt("notes");
            if (!(notes instanceof String) || ((String) notes).length() > 2000)
                throw new Exception("Notes must be text under 2,000 characters.");
            plan.notes = ((String) notes).trim();
        }
        return plan;
    }

    private static String string(Object value, String label, int length) throws Exception {
        if (!(value instanceof String) || ((String) value).trim().isEmpty() || ((String) value).length() > length)
            throw new Exception(label + " must be text, 1–" + length + " characters.");
        return ((String) value).trim();
    }
    private static int integer(Object value, String label, int min, int max) throws Exception {
        if (!(value instanceof Number)) throw new Exception(label + " must be a whole number (" + min + "–" + max + ").");
        double n = ((Number) value).doubleValue();
        if (!Double.isFinite(n) || n != Math.floor(n) || n < min || n > max)
            throw new Exception(label + " must be a whole number (" + min + "–" + max + ").");
        return (int) n;
    }
    static String encode(WorkoutPlan plan) throws Exception {
        JSONObject json = new JSONObject();
        json.put("format", "tempo-workout"); json.put("version", 1);
        json.put("name", plan.name); json.put("notes", plan.notes);
        JSONArray stages = new JSONArray();
        for (WorkoutPlan.Phase phase : plan.phases) {
            JSONObject stage = new JSONObject();
            stage.put("name", phase.name); stage.put("seconds", phase.seconds); stage.put("zone", phase.zone);
            stages.put(stage);
        }
        json.put("stages", stages);
        return json.toString();
    }
}
