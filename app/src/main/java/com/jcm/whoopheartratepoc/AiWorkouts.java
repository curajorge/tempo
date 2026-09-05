package com.jcm.whoopheartratepoc;
import android.app.Activity;
import android.content.*;
import android.text.InputType;
import android.widget.*;
import java.util.ArrayList;
import org.json.JSONArray;

final class AiWorkouts {
    interface Saved { void accept(WorkoutPlan plan); }
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Saved saved;
    private final int[][] ranges;
    AiWorkouts(Activity activity,int[][] ranges,Saved saved){
        this.activity=activity;this.ranges=ranges;this.saved=saved;
        prefs=activity.getSharedPreferences("tempo_workouts",0);
    }
    static ArrayList<WorkoutPlan> load(Context c) {
        ArrayList<WorkoutPlan> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(c.getSharedPreferences("tempo_workouts", 0).getString("library", "[]"));
            for (int i = 0; i < array.length(); i++) {
                try { result.add(WorkoutJson.parse(array.getString(i))); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return result;
    }

    void show(){
        FormSheet form=new FormSheet(activity,"Create with WHOOP AI","Plan with WHOOP. Train with Tempo.");
        form.item("Copy a prompt","Describe your goal for WHOOP AI",()->{form.dismiss();prompt();});
        form.item("Import a response","Paste, review, then save to your library",()->{form.dismiss();importResponse();});
        form.show();
    }
    void prompt(){
        FormSheet form=new FormSheet(activity,"Ask WHOOP AI","Tell it what you want to train today.");
        EditText goal=form.input("Your workout goal","30 minutes focused on Zone 2",prefs.getString("goal",""),true);
        form.label("The prompt includes your current zone ranges. Match them to WHOOP in Zones before copying.");
        TextView error=form.error();
        form.action("Copy prompt",true,()->{
            String request=goal.getText().toString().trim();
            if(request.isEmpty()){error.setText("Enter a workout goal first.");return;}
            prefs.edit().putString("goal",request).apply();
            ClipboardManager cb=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("Tempo workout prompt",promptText(request,ranges)));
            error.setTextColor(FormSheet.ACCENT);error.setText("Copied. Paste this into WHOOP AI, then import its response.");
        });
        form.action("I have a response · Import",false,()->{form.dismiss();importResponse();});
        form.setOnDismissListener(d->prefs.edit().putString("goal",goal.getText().toString()).apply());
        form.show();
    }
    static String promptText(String goal, int[][] ranges) {
        StringBuilder zones = new StringBuilder();
        for (int i = 0; i < 5; i++) zones.append("Zone ").append(i+1).append(": ")
            .append(ranges[i][0]).append("–").append(ranges[i][1]).append(" bpm\n");
        return "Create a workout for this goal: " + goal + "\n\n"
            + "Use my WHOOP context, recovery and recent training to guide your recommendation. "
            + "Tempo will use these user-configured zone boundaries:\n" + zones
            + "If these differ from my WHOOP zones, do not silently remap them; explain the mismatch in notes.\n"
            + "Return ONLY one JSON object, no prose or markdown. Format must be tempo-workout, version 1. "
            + "Each stage needs a name, seconds (whole number 1–21600), and zone (whole number 1–5). "
            + "Use 1–100 stages, at most 86400 seconds total. Expand repeated intervals into individual stages. "
            + "Use fixed-duration stages; Tempo does not support distance or time-in-zone timers yet. "
            + "Use a name of at most 120 characters and optional notes of at most 2000 characters. "
            + "No comments, trailing commas, or extra fields. Follow this schema (example only, adapt the workout):\n"
            + "{\"format\":\"tempo-workout\",\"version\":1,\"name\":\"Aerobic base\","
            + "\"notes\":\"Keep effort steady.\",\"stages\":["
            + "{\"name\":\"Warm-up\",\"seconds\":300,\"zone\":1},"
            + "{\"name\":\"Steady\",\"seconds\":1200,\"zone\":2},"
            + "{\"name\":\"Cool-down\",\"seconds\":300,\"zone\":1}]}";
    }

    void importResponse(){
        FormSheet form=new FormSheet(activity,"Import workout","Bring your WHOOP AI plan into Tempo.");
        EditText input=form.input("WHOOP AI response","Paste the complete workout response here",prefs.getString("draft",""),true);
        input.setTextSize(14);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        form.label("A plain JSON response or a complete code block both work. You'll review the stages before saving.");
        TextView error=form.error();
        form.action("Review workout",true,()->{
            try{preview(WorkoutJson.parse(input.getText().toString()),form,input);}
            catch(Exception e){error.setText(e.getMessage());}
        });
        form.action("Paste from clipboard",false,()->{
            ClipboardManager cb=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip=cb.getPrimaryClip();
            if(clip!=null&&clip.getItemCount()>0)input.setText(clip.getItemAt(0).coerceToText(activity));
            else error.setText("Your clipboard is empty.");
        });
        form.setOnDismissListener(d->prefs.edit().putString("draft",input.getText().toString()).apply());
        form.show();
    }
    private void stages(FormSheet form,WorkoutPlan plan){
        if(!plan.notes.isEmpty())form.label(plan.notes);
        for(int i=0;i<plan.phases.length;i++){
            WorkoutPlan.Phase p=plan.phases[i];int[] range=ranges[p.zone-1];
            TextView stage=form.label((i+1)+". "+p.name+"\n"+time(p.seconds)+" · Zone "+p.zone+" · "+range[0]+"–"+range[1]+" bpm");
            stage.setTextColor(FormSheet.INK);
        }
    }
    private void preview(WorkoutPlan plan,FormSheet editor,EditText input){
        FormSheet form=new FormSheet(activity,plan.name,plan.phases.length+" stages · "+time(plan.totalSeconds())+" total");
        stages(form,plan);TextView error=form.error();
        form.action("Save to my workouts",true,()->{
            try{
                String encoded=WorkoutJson.encode(plan);
                for(WorkoutPlan existing:load(activity))if(WorkoutJson.encode(existing).equals(encoded))
                    throw new Exception("This exact workout is already in your library.");
                WorkoutStore.change(activity,null,plan);
                input.setText("");editor.dismiss();form.dismiss();saved.accept(plan);
                Toast.makeText(activity,"Workout saved and selected",Toast.LENGTH_SHORT).show();
            }catch(Exception e){error.setText(e.getMessage());}
        });
        form.action("Back to edit",false,form::dismiss);form.show();
    }
    void library(WorkoutPlan[] plans){
        FormSheet form=new FormSheet(activity,"Your workouts","Choose a workout or manage your saved plans.");
        form.label("My workouts");
        if(plans.length<=3)form.label("Your imported workouts will appear here.");
        for(int i=3;i<plans.length;i++){
            WorkoutPlan p=plans[i];form.item(p.name,p.phases.length+" stages · "+time(p.totalSeconds()),()->{
                form.dismiss();details(p,true);
            });
        }
        form.label("Included workouts");
        for(int i=0;i<Math.min(3,plans.length);i++){
            WorkoutPlan p=plans[i];form.item(p.name,p.phases.length+" stages · "+time(p.totalSeconds()),()->{
                form.dismiss();details(p,false);
            });
        }
        form.action("Create with WHOOP AI",true,()->{form.dismiss();show();});form.show();
    }
    private void details(WorkoutPlan plan,boolean editable){
        FormSheet form=new FormSheet(activity,plan.name,plan.phases.length+" stages · "+time(plan.totalSeconds())+" total");
        stages(form,plan);TextView error=form.error();
        form.action("Use this workout",true,()->{form.dismiss();saved.accept(plan);});
        if(editable)form.action("Edit workout",false,()->{form.dismiss();edit(plan);});
        form.action("Duplicate workout",false,()->{
            try{
                WorkoutPlan copy=WorkoutJson.parse(WorkoutJson.encode(plan));
                WorkoutPlan named=new WorkoutPlan((copy.name.length()>110?copy.name.substring(0,110):copy.name)+" (copy)",copy.phases);
                named.notes=copy.notes;WorkoutStore.change(activity,null,named);
                form.dismiss();saved.accept(named);details(named,true);
            }catch(Exception e){error.setText(e.getMessage());}
        });
        if(editable){
            Button delete=form.action("Delete workout",false,()->confirmDelete(plan,form));
            delete.setTextColor(0xffff9ca8);
        }
        form.show();
    }
    private void confirmDelete(WorkoutPlan plan,FormSheet details){
        FormSheet form=new FormSheet(activity,"Delete this workout?","Only this saved plan will be removed.");
        form.label(plan.name);form.label("You can import it again from WHOOP AI. Included workouts are not affected.");
        TextView error=form.error();
        form.action("Delete workout",true,()->{
            try{WorkoutStore.change(activity,plan,null);form.dismiss();details.dismiss();saved.accept(null);
                Toast.makeText(activity,"Workout deleted",Toast.LENGTH_SHORT).show();
            }catch(Exception e){error.setText(e.getMessage());}
        });
        form.action("Keep workout",false,form::dismiss);form.show();
    }
    private void edit(WorkoutPlan plan){
        FormSheet form=new FormSheet(activity,"Edit workout","Update the name, notes, or individual stages.");
        EditText name=form.input("Workout name","Give this workout a name",plan.name,false);
        EditText notes=form.input("Notes","Optional coaching notes",plan.notes,true);
        EditText[][] fields=new EditText[plan.phases.length][3];
        for(int i=0;i<plan.phases.length;i++){
            WorkoutPlan.Phase p=plan.phases[i];
            fields[i][0]=form.input("Stage "+(i+1),"Stage name",p.name,false);
            fields[i][1]=form.input("Duration in seconds","300",String.valueOf(p.seconds),false);
            fields[i][1].setInputType(InputType.TYPE_CLASS_NUMBER);
            fields[i][2]=form.input("Target zone · 1 to 5","2",String.valueOf(p.zone),false);
            fields[i][2].setInputType(InputType.TYPE_CLASS_NUMBER);
        }
        TextView error=form.error();
        form.action("Save changes",true,()->{
            try{
                WorkoutPlan.Phase[] phases=new WorkoutPlan.Phase[fields.length];
                for(int i=0;i<fields.length;i++)phases[i]=new WorkoutPlan.Phase(fields[i][0].getText().toString(),
                        Integer.parseInt(fields[i][2].getText().toString().trim()),Integer.parseInt(fields[i][1].getText().toString().trim()));
                WorkoutPlan changed=new WorkoutPlan(name.getText().toString().trim(),phases);changed.notes=notes.getText().toString();
                changed=WorkoutJson.parse(WorkoutJson.encode(changed));
                WorkoutStore.change(activity,plan,changed);form.dismiss();saved.accept(changed);
                Toast.makeText(activity,"Changes saved",Toast.LENGTH_SHORT).show();
            }catch(Exception e){error.setText(e instanceof NumberFormatException?"Use whole numbers for seconds and zones.":e.getMessage());}
        });
        form.action("Cancel",false,form::dismiss);form.show();
    }
    static String time(int seconds){return String.format(java.util.Locale.US,"%d:%02d",seconds/60,seconds%60);}
}
