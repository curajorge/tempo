package com.jcm.whoopheartratepoc;

import android.content.Context;
import java.util.ArrayList;
import org.json.JSONArray;

/** Match exact stored records so a stale screen cannot modify a different workout. */
final class WorkoutStore {
    static void change(Context context, WorkoutPlan original, WorkoutPlan replacement) throws Exception {
        android.content.SharedPreferences prefs=context.getSharedPreferences("tempo_workouts",0);
        JSONArray records=new JSONArray(prefs.getString("library","[]"));
        String target=original==null?null:WorkoutJson.encode(original);
        String updated=replacement==null?null:WorkoutJson.encode(replacement);
        if(replacement!=null) WorkoutJson.parse(updated);
        JSONArray result=new JSONArray();boolean found=original==null;
        for(int i=0;i<records.length();i++){
            String raw=records.getString(i);
            String canonical;
            try{canonical=WorkoutJson.encode(WorkoutJson.parse(raw));}catch(Exception e){canonical=raw;}
            if(target!=null && target.equals(canonical) && !found){
                found=true;if(updated!=null)result.put(updated);
            }else result.put(raw);
        }
        if(!found)throw new Exception("This workout changed. Reopen the library and try again.");
        if(original==null && replacement!=null){
            if(records.length()>=100)throw new Exception("Library is full. Delete a workout first.");
            result.put(updated);
        }
        if(!prefs.edit().putString("library",result.toString()).commit())throw new Exception("Could not save. Please try again.");
    }
}
