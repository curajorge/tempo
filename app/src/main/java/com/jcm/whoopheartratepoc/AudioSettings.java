package com.jcm.whoopheartratepoc;

import android.content.Context;
import android.content.SharedPreferences;

final class AudioSettings {
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("tempo_coaching",0);}
    static boolean muted(Context c){return prefs(c).getBoolean("muted",false);}
    static boolean stages(Context c){return prefs(c).getBoolean("stage_audio",true);}
    static boolean zones(Context c){return prefs(c).getBoolean("zone_audio",true);}
    static int interval(Context c){int v=prefs(c).getInt("cue_seconds",45);return v==30||v==60?v:45;}
}
