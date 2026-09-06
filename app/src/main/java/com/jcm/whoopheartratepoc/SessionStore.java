package com.jcm.whoopheartratepoc;

import android.content.Context;
import org.json.*;

/** Private on-device data only. Saving by session ID makes repeated finish callbacks safe. */
final class SessionStore {
    static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("tempo_sessions",0);}
    static void checkpoint(Context c,TrainingSession s) throws Exception {
        if(!prefs(c).edit().putString("active",s.json().toString()).commit())throw new Exception("Could not save session");
    }
    static TrainingSession recover(Context c) throws Exception {
        String raw=prefs(c).getString("active",null);return raw==null?null:TrainingSession.restore(raw);
    }
    static JSONArray history(Context c) throws JSONException {return new JSONArray(prefs(c).getString("history","[]"));}
    static void finish(Context c,TrainingSession s) throws Exception {
        JSONArray old=history(c), updated=new JSONArray();
        updated.put(s.json().put("ended",System.currentTimeMillis()));
        for(int i=0;i<old.length();i++)if(!old.getJSONObject(i).getString("id").equals(s.id))updated.put(old.getJSONObject(i));
        if(!prefs(c).edit().putString("history",updated.toString()).remove("active").commit())throw new Exception("Could not save history");
    }
    static void delete(Context c,String id) throws Exception {
        JSONArray old=history(c), keep=new JSONArray();
        for(int i=0;i<old.length();i++)if(!id.equals(old.getJSONObject(i).getString("id")))keep.put(old.getJSONObject(i));
        if(!prefs(c).edit().putString("history",keep.toString()).commit())throw new Exception("Could not delete session");
    }
}
