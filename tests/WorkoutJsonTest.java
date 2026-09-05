package com.jcm.whoopheartratepoc;

public final class WorkoutJsonTest {
    static int passed;
    public static void main(String[] args) throws Exception {
        String valid = "{\"format\":\"tempo-workout\",\"version\":1,\"name\":\"AI workout\","
            + "\"notes\":\"Easy pace\",\"stages\":[{\"name\":\"Warm\",\"seconds\":300,\"zone\":1}]}";
        check(WorkoutJson.parse(valid).totalSeconds() == 300);
        check(WorkoutJson.parse("```json\n" + valid + "\n```").totalSeconds() == 300);
        check(WorkoutJson.parse(WorkoutJson.encode(WorkoutJson.parse(valid))).notes.equals("Easy pace"));
        reject(valid.replace("\"zone\":1", "\"zone\":6"));
        reject(valid.replace("\"zone\":1", "\"zone\":0"));
        reject(valid.replace("\"zone\":1", "\"zone\":true"));
        reject(valid.replace("\"seconds\":300", "\"seconds\":0"));
        reject(valid.replace("\"seconds\":300", "\"seconds\":1.2"));
        reject(valid.replace("\"seconds\":300", "\"seconds\":\"300\""));
        reject(valid.replace("\"version\":1", "\"version\":2"));
        reject(valid.replace("\"name\":\"AI workout\"", "\"name\":\"\""));
        reject(valid + " trailing prose");
        reject("");
        reject("{}");
        reject("{\"format\":\"tempo-workout\",\"version\":1,\"name\":\"Empty\",\"stages\":[]}");
        System.out.println(passed + " workout JSON tests passed");
    }
    static void check(boolean condition) { if (!condition) throw new AssertionError(); passed++; }
    static void reject(String value) throws Exception {
        try { WorkoutJson.parse(value); } catch (Exception expected) { passed++; return; }
        throw new AssertionError("Invalid input accepted");
    }
}
