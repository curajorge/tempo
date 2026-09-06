package com.jcm.whoopheartratepoc;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int PERMISSION_REQUEST = 42;
    private static final long SCAN_DURATION_MS = 15_000L;
    private static final UUID HEART_RATE_SERVICE = uuid16("180D");
    private static final UUID HEART_RATE_MEASUREMENT = uuid16("2A37");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = uuid16("2902");

    private static final int NIGHT = Color.rgb(10, 17, 29);
    private static final int PANEL = Color.rgb(20, 31, 48);
    private static final int IVORY = Color.rgb(245, 246, 242);
    private static final int MUTED = Color.rgb(139, 146, 148);
    private static final int CYAN = Color.rgb(102, 236, 196);
    private static final int AMBER = Color.rgb(255, 138, 61);
    private static final int CORAL = Color.rgb(237, 76, 92);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, BluetoothDevice> devices = new LinkedHashMap<>();
    private final ArrayList<BluetoothDevice> scanDevices = new ArrayList<>();
    private final ArrayList<String> scanLabels = new ArrayList<>();

    private WorkoutPlan[] workouts = {
            new WorkoutPlan("Ignition test",
                    phase("Warm", 1, 15), phase("Cruise", 2, 20),
                    phase("Build", 3, 20), phase("Surge", 4, 15), phase("Recover", 1, 20)),
            new WorkoutPlan("Zone 2 engine",
                    phase("Warm up", 1, 120), phase("Aerobic cruise", 2, 900),
                    phase("Cool down", 1, 180)),
            new WorkoutPlan("Progressive drive",
                    phase("Warm up", 1, 300), phase("Cruise", 2, 480),
                    phase("Tempo", 3, 300), phase("Threshold", 4, 180),
                    phase("Settle", 2, 240), phase("Cool down", 1, 300))
    };

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private boolean scanning;
    private boolean destroyed;
    private boolean heartRateSubscribed;
    private ArrayAdapter<String> scanAdapter;
    private AlertDialog scanDialog;

    private TrainingView trainingView;
    private TextView connectionText;
    private TextView targetText;
    private TextView countdownText;
    private TextView cueText;
    private TextView statsText;
    private Button workoutButton;
    private Button zonesButton;
    private Button primaryButton;
    private Button driveTab;
    private Button reactorTab;
    private Button previousStage, restartStage, nextStage;

    private int visualizationMode = TrainingView.DRIVE;
    private Button muteButton;
    private int selectedWorkout;
    private int[][] customZones;
    private boolean useCustomZones;
    private int restingHr = 60;
    private int maxHr = 190;
    private int currentBpm;
    private boolean sessionRunning;
    private boolean sessionPaused;
    private int phaseIndex;
    private long phaseStartedAt;
    private long pausedAt;
    private long targetMillis;
    private long lastMetricAt;
    private long heartRateSum;
    private int heartRateSamples;
    private int peakHeartRate;
    private String stageKey = "";
    private TextToSpeech textToSpeech;
    private boolean speechReady;
    private TrainingSession displayedSession;
    private boolean serviceStarting;
    private final Runnable serviceRefresh = new Runnable() {
        public void run() {
            syncSession();
            if (!destroyed) handler.postDelayed(this, 500);
        }
    };

    private final Runnable stopScanRunnable = this::stopScan;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restingHr = getPreferences(MODE_PRIVATE).getInt("resting_hr", 60);
        maxHr = getPreferences(MODE_PRIVATE).getInt("max_hr", 190);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(NIGHT);
        getWindow().setNavigationBarColor(NIGHT);
        java.util.ArrayList<WorkoutPlan> allPlans = new java.util.ArrayList<>(java.util.Arrays.asList(workouts));
        allPlans.addAll(AiWorkouts.load(this)); workouts = allPlans.toArray(new WorkoutPlan[0]);
        customZones = new int[5][2];
        boolean hasZones = getPreferences(MODE_PRIVATE).getBoolean("custom_zones", false);
        for (int i = 0; i < 5; i++) {
            int[] calculated = zoneRange(i + 1);
            customZones[i][0] = hasZones ? getPreferences(MODE_PRIVATE).getInt("zone_low_" + i, calculated[0]) : calculated[0];
            customZones[i][1] = hasZones ? getPreferences(MODE_PRIVATE).getInt("zone_high_" + i, calculated[1]) : calculated[1];
        }
        useCustomZones = hasZones;
        buildUi();
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = manager == null ? null : manager.getAdapter();
        setConnection(bluetoothAdapter == null ? "Bluetooth unavailable" : "Sensor not connected", false);
        textToSpeech = new TextToSpeech(this, this);
        renderState();
        handler.postDelayed(this::reconnectAutomatically, 350);
        handler.post(serviceRefresh);
        if (WorkoutService.instance == null) handler.postDelayed(this::offerRecovery, 600);
    }

    private TextView inZoneValue, averageValue, peakValue, nextText;
    private LinearLayout stageList;
    private void buildUi() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL); outer.setBackgroundColor(NIGHT);
        outer.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true); scroll.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(16), dp(22), dp(20));
        scroll.addView(root); outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout header = row(); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Tempo", 30, IVORY);
        title.setTypeface(getResources().getFont(R.font.geist_medium));
        header.addView(title, weighted(1, dp(48)));
        Button library = button("Workouts", false);
        library.setPadding(dp(6), 0, dp(6), 0);
        library.setSingleLine(true);
        library.setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        library.setOnClickListener(v -> chooseWorkout());
        LinearLayout.LayoutParams libraryParams = sized(dp(88), dp(48));libraryParams.rightMargin=dp(8);
        header.addView(library, libraryParams);
        Button sensor = button("Sensor", false);
        sensor.setOnClickListener(v -> openSensor());
        header.addView(sensor, sized(dp(76), dp(48)));
        root.addView(header);
        connectionText = label("Connect your sensor to get started", 13, MUTED);
        connectionText.setPadding(0, dp(6), 0, dp(16));
        connectionText.setOnClickListener(v -> openSensor());
        root.addView(connectionText);

        LinearLayout tools = row();
        muteButton = button("Mute audio", false);
        muteButton.setOnClickListener(v -> setMuted(!AudioSettings.muted(this)));
        tools.addView(muteButton, weighted(1, dp(44)));
        Button settings = button("Settings", false);settings.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams settingsParams=weighted(1,dp(44));settingsParams.leftMargin=dp(8);
        tools.addView(settings,settingsParams);
        LinearLayout.LayoutParams toolsParams=match(dp(44));toolsParams.bottomMargin=dp(14);
        root.addView(tools,toolsParams);updateMuteButton();

        LinearLayout tabs = row();
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4)); tabs.setBackground(surface(PANEL, 16));
        driveTab = button("Drive", true); reactorTab = button("Pulse", false);
        driveTab.setOnClickListener(v -> selectVisualization(0));
        reactorTab.setOnClickListener(v -> selectVisualization(1));
        tabs.addView(driveTab, weighted(1, dp(42)));
        tabs.addView(reactorTab, weighted(1, dp(42)));
        root.addView(tabs, match(dp(50)));

        if (trainingView == null) trainingView = new TrainingView(this);
        else if (trainingView.getParent() instanceof android.view.ViewGroup)
            ((android.view.ViewGroup) trainingView.getParent()).removeView(trainingView);
        LinearLayout.LayoutParams gaugeParams = match(dp(280));
        gaugeParams.topMargin = dp(12); gaugeParams.bottomMargin = dp(12);
        root.addView(trainingView, gaugeParams);

        LinearLayout phasePanel = new LinearLayout(this); phasePanel.setOrientation(LinearLayout.VERTICAL);
        phasePanel.setPadding(dp(18), dp(16), dp(18), dp(16));
        phasePanel.setBackground(surface(PANEL, 20));
        LinearLayout phaseRow = row();
        targetText = label("", 16, IVORY); targetText.setGravity(Gravity.CENTER_VERTICAL);
        countdownText = label("", 28, IVORY); countdownText.setGravity(Gravity.END);
        countdownText.setTypeface(getResources().getFont(R.font.geist_medium));
        countdownText.setFontFeatureSettings("tnum");
        countdownText.setSingleLine(true);
        countdownText.setAutoSizeTextTypeUniformWithConfiguration(18, 28, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        phaseRow.addView(targetText, weighted(3, dp(42)));
        phaseRow.addView(countdownText, weighted(1, dp(42)));
        phasePanel.addView(phaseRow);
        cueText = label("", 14, CYAN); phasePanel.addView(cueText);
        nextText = label("", 12, MUTED); nextText.setPadding(0, dp(9), 0, 0);
        phasePanel.addView(nextText); root.addView(phasePanel);
        LinearLayout navigation = row();
        navigation.setPadding(0, dp(10), 0, 0);
        previousStage = button("Previous", false);
        restartStage = button("Restart", false);
        nextStage = button("Next", false);
        previousStage.setOnClickListener(v -> jumpToStage(phaseIndex - 1));
        restartStage.setOnClickListener(v -> jumpToStage(Math.min(phaseIndex, workouts[selectedWorkout].phases.length - 1)));
        nextStage.setOnClickListener(v -> {
            if (phaseIndex + 1 < workouts[selectedWorkout].phases.length) jumpToStage(phaseIndex + 1);
            else if (sessionRunning) new AlertDialog.Builder(this).setTitle("Finish this workout?")
                .setMessage("End the final stage now? Your current metrics will remain visible.")
                .setPositiveButton("Finish", (d, w) -> {
                    phaseIndex = workouts[selectedWorkout].phases.length; finishWorkout();
                }).setNegativeButton("Keep training", null).show();
        });
        navigation.addView(previousStage, weighted(1, dp(48)));
        LinearLayout.LayoutParams middle = weighted(1, dp(48)); middle.leftMargin = dp(6); middle.rightMargin = dp(6);
        navigation.addView(restartStage, middle);
        navigation.addView(nextStage, weighted(1, dp(48))); root.addView(navigation);

        LinearLayout metrics = row(); metrics.setPadding(0, dp(20), 0, dp(20));
        inZoneValue = metric(metrics, "Time in target");
        averageValue = metric(metrics, "Average bpm");
        peakValue = metric(metrics, "Peak bpm");
        root.addView(metrics);
        statsText = new TextView(this); // Kept for the existing summary calculation.
        LinearLayout planRow = row();
        workoutButton = button(workouts[selectedWorkout].toString() + "  ⌄", false);
        workoutButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        workoutButton.setOnClickListener(v -> chooseWorkout());
        planRow.addView(workoutButton, weighted(1, dp(52)));
        zonesButton = button("Zones", false); zonesButton.setOnClickListener(v -> editZones());
        LinearLayout.LayoutParams zp = sized(dp(76), dp(52)); zp.leftMargin = dp(8);
        planRow.addView(zonesButton, zp); root.addView(planRow);
        stageList = new LinearLayout(this); stageList.setOrientation(LinearLayout.VERTICAL);
        stageList.setPadding(dp(4), dp(12), dp(4), dp(4));
        root.addView(stageList);
        Button ai = button("Create with WHOOP AI", false);
        ai.setOnClickListener(v -> openAiWorkouts());
        LinearLayout.LayoutParams aip = match(dp(50)); aip.topMargin = dp(10);
        root.addView(ai, aip);
        LinearLayout extras = row();
        Button history = button("History", false); history.setOnClickListener(v -> showHistory());
        extras.addView(history, weighted(1, dp(48)));
        LinearLayout.LayoutParams extraParams=match(dp(48));extraParams.topMargin=dp(10);
        root.addView(extras,extraParams);
        TextView note = label("Active workouts continue with the screen locked. Use the training notification to pause or stop.", 12, MUTED);
        note.setPadding(0, dp(10), 0, 0); root.addView(note);
        TextView independence = label("Tempo is an independent app. Not affiliated with or endorsed by WHOOP.", 12, MUTED);
        independence.setPadding(0, dp(8), 0, dp(8)); root.addView(independence);

        LinearLayout controls = row(); controls.setPadding(dp(22), dp(12), dp(22), dp(12));
        controls.setBackground(surface(NIGHT, 0));
        primaryButton = button("Connect to start", true);
        primaryButton.setOnClickListener(v -> {
            if (currentBpm == 0 && !sessionRunning) openSensor(); else toggleWorkout();
        });
        controls.addView(primaryButton, weighted(1, dp(54)));
        Button reset = button("Reset", false); reset.setOnClickListener(v -> resetWorkout());
        LinearLayout.LayoutParams rp = sized(dp(82), dp(54)); rp.leftMargin = dp(10);
        controls.addView(reset, rp); outer.addView(controls);
        setContentView(outer);
        selectVisualization(visualizationMode);
    }
    private GradientDrawable surface(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);shape.setCornerRadius(dp(radius)); return shape;
    }
    private TextView metric(LinearLayout parent, String name) {
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        TextView value = label("—", 23, IVORY);
        value.setTypeface(getResources().getFont(R.font.geist_medium));
        value.setFontFeatureSettings("tnum");
        column.addView(value); column.addView(label(name, 11, MUTED));
        parent.addView(column, weighted(1, -2)); return value;
    }

    private void selectVisualization(int mode) {
        visualizationMode = mode;
        trainingView.setMode(mode);
        styleButton(driveTab, mode == TrainingView.DRIVE);
        styleButton(reactorTab, mode == TrainingView.REACTOR);
        driveTab.setBackground(surface(mode==TrainingView.DRIVE?0xff243447:PANEL,12));
        reactorTab.setBackground(surface(mode==TrainingView.REACTOR?0xff243447:PANEL,12));
        driveTab.setTextColor(mode==TrainingView.DRIVE?CYAN:MUTED);
        reactorTab.setTextColor(mode==TrainingView.REACTOR?CYAN:MUTED);
        driveTab.setSelected(mode==TrainingView.DRIVE);reactorTab.setSelected(mode==TrainingView.REACTOR);
    }

    private AiWorkouts workoutManager() {
        int[][] ranges = new int[5][2]; for (int i=0;i<5;i++) ranges[i] = zoneRange(i+1);
        return new AiWorkouts(this, ranges, plan -> {
            java.util.ArrayList<WorkoutPlan> all = new java.util.ArrayList<>();
            for (int i=0;i<3;i++) all.add(workouts[i]);
            all.addAll(AiWorkouts.load(this)); workouts = all.toArray(new WorkoutPlan[0]);
            selectedWorkout = 0;
            if (plan != null) {
                for (int i=0;i<workouts.length;i++) {
                    try { if (WorkoutJson.encode(workouts[i]).equals(WorkoutJson.encode(plan))) {selectedWorkout=i;break;} }
                    catch (Exception ignored) { }
                }
            }
            workoutButton.setText(workouts[selectedWorkout].toString()+"  ⌄"); stageKey=""; resetWorkout();
        });
    }

    private void chooseWorkout() {
        if (sessionRunning) {
            Toast.makeText(this, "Reset the current workout before managing plans.", Toast.LENGTH_SHORT).show();
            return;
        }
        workoutManager().library(workouts);
    }

    private void openAiWorkouts() {
        if (sessionRunning) {
            Toast.makeText(this, "Reset the current workout before importing a new one.", Toast.LENGTH_LONG).show();
            return;
        }
        workoutManager().show();
    }

    private void editZones() {
        if (sessionRunning) {
            Toast.makeText(this, "Reset the workout before changing zones.", Toast.LENGTH_SHORT).show(); return;
        }
        FormSheet form = new FormSheet(this, "Your heart-rate zones", "Match these BPM ranges to your WHOOP app.");
        EditText[][] inputs = new EditText[5][2];
        for (int i=0;i<5;i++) {
            form.label("Zone "+(i+1)+" · minimum / maximum bpm");
            LinearLayout row = row(); int[] range=zoneRange(i+1);
            for (int j=0;j<2;j++) {
                EditText field=new EditText(this);form.styleInput(field);
                field.setInputType(InputType.TYPE_CLASS_NUMBER);field.setText(String.valueOf(range[j]));
                field.setContentDescription("Zone "+(i+1)+(j==0?" minimum bpm":" maximum bpm"));
                field.setSelectAllOnFocus(true);inputs[i][j]=field;
                LinearLayout.LayoutParams p=weighted(1,dp(56));if(j==1)p.leftMargin=dp(10);row.addView(field,p);
            }
            form.body.addView(row);
        }
        TextView error=form.error();
        form.action("Save zones",true,()->{
            try {
                int[][] candidate=new int[5][2];
                for(int i=0;i<5;i++){
                    candidate[i][0]=Integer.parseInt(inputs[i][0].getText().toString().trim());
                    candidate[i][1]=Integer.parseInt(inputs[i][1].getText().toString().trim());
                    if(candidate[i][0]<20||candidate[i][1]>250||candidate[i][0]>candidate[i][1])
                        throw new Exception("Zone "+(i+1)+": minimum must be ≤ maximum, between 20 and 250 bpm.");
                    if(i>0&&candidate[i][0]<=candidate[i-1][1])throw new Exception("Zone ranges must ascend without overlapping.");
                }
                android.content.SharedPreferences.Editor preferences=getPreferences(MODE_PRIVATE).edit();
                for(int i=0;i<5;i++)preferences.putInt("zone_low_"+i,candidate[i][0]).putInt("zone_high_"+i,candidate[i][1]);
                preferences.putBoolean("custom_zones",true).apply();customZones=candidate;useCustomZones=true;
                renderState();form.dismiss();
            }catch(Exception e){error.setText(e instanceof NumberFormatException?"Enter a whole BPM number in every field.":e.getMessage());}
        });
        form.show();
    }

    private EditText numberField(String hint, int value) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(String.valueOf(value));
        field.setSelectAllOnFocus(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        return field;
    }

    private void toggleWorkout() {
        if (WorkoutService.instance != null && WorkoutService.latest != null && !WorkoutService.latest.done) {
            WorkoutService.instance.toggle();syncSession();return;
        }
        if (serviceStarting) return;
        startWorkout();
    }

    private void startWorkout() {
        if (serviceStarting) return;
        if (WorkoutService.instance != null) {
            Toast.makeText(this, "Finishing the previous session. Try again in a moment.", Toast.LENGTH_SHORT).show();return;
        }
        try {if(SessionStore.recover(this)!=null){offerRecovery();return;}}
        catch(Exception e){Toast.makeText(this,"An earlier session could not be read. It has not been overwritten.",Toast.LENGTH_LONG).show();return;}
        if (currentBpm == 0) { openSensor(); return; }
        if (!notificationRequested && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 43);
            Toast.makeText(this, "Notifications provide screen-off controls. Tap Start again after choosing.", Toast.LENGTH_LONG).show();
            notificationRequested = true;return;
        }
        beginBackgroundSession();
    }

    private boolean notificationRequested;
    private void syncSession() {
        TrainingSession s=WorkoutService.latest;
        if (s==null) return;
        if (WorkoutService.instance==null && !s.done) {
            WorkoutService.latest=null;serviceStarting=false;sessionRunning=false;sessionPaused=true;currentBpm=0;
            renderState();offerRecovery();return;
        }
        serviceStarting=false;
        if (displayedSession!=s) {
            stopScan();closeGatt();
            int match=-1;
            try {for(int i=0;i<workouts.length;i++)if(WorkoutJson.encode(workouts[i]).equals(WorkoutJson.encode(s.plan))){match=i;break;}}
            catch(Exception ignored){}
            if(match<0){workouts=java.util.Arrays.copyOf(workouts,workouts.length+1);match=workouts.length-1;workouts[match]=s.plan;}
            selectedWorkout=match;displayedSession=s;stageKey="";
            workoutButton.setText(workouts[selectedWorkout].toString()+"  ⌄");
        }
        sessionRunning=!s.done;sessionPaused=s.paused;phaseIndex=s.done?s.plan.phases.length:s.stage;
        long now=SystemClock.elapsedRealtime();phaseStartedAt=now-s.stageElapsed;pausedAt=now;
        targetMillis=s.target;heartRateSum=s.average();heartRateSamples=s.measured>0?1:0;peakHeartRate=s.peak;
        currentBpm=s.waiting?0:s.bpm;lastHrAt=s.lastHr;
        connectionText.setText(WorkoutService.status);connectionText.setTextColor(s.waiting?MUTED:CYAN);
        renderState();
        primaryButton.setText(s.done?"Start again":s.waiting?(s.autoResume?"Pause auto-resume":"Waiting for sensor"):s.paused?"Resume":"Pause");
        workoutButton.setEnabled(s.done);zonesButton.setEnabled(s.done);
        if(s.waiting && !s.done)cueText.setText("Signal missing · timer paused");
        if(s.done)cueText.setText(WorkoutService.status.contains("save failed")?"History save failed · reopen Tempo to retry":s.outcome.equals("completed")?"Workout complete · saved to History":"Session stopped · saved to History");
        if(s.done && WorkoutService.instance==null){
            WorkoutService.latest=null;displayedSession=null;currentBpm=0;
            handler.post(this::reconnectAutomatically);
        }
    }
    private void offerRecovery() {
        if(destroyed || WorkoutService.instance!=null || serviceStarting)return;
        try {
            TrainingSession s=SessionStore.recover(this);if(s==null)return;
            FormSheet f=new FormSheet(this,"Resume your session?",s.plan.name);
            TextView error=f.error();
            f.label(formatTime(s.elapsed/1000)+" recorded. Time while Tempo was closed is not counted.");
            if(!s.done)f.action("Restore paused session",true,()->{
                try{launchSession(s,false);f.dismiss();}catch(Exception e){error.setText("Could not restore: "+e.getMessage());}
            });
            f.action("Save to History",false,()->{
                try{s.finish(SystemClock.elapsedRealtime());SessionStore.finish(this,s);f.dismiss();showHistory();}
                catch(Exception e){error.setText("Could not save: "+e.getMessage());}
            });
            f.show();
        }catch(Exception e){Toast.makeText(this,"Saved session could not be read. It has not been deleted.",Toast.LENGTH_LONG).show();}
    }
    private void setMuted(boolean muted) {
        AudioSettings.prefs(this).edit().putBoolean("muted",muted).apply();
        if(muted && textToSpeech!=null)textToSpeech.stop();
        updateMuteButton();
    }
    private void updateMuteButton() {
        if(muteButton==null)return;
        boolean muted=AudioSettings.muted(this);
        muteButton.setText(muted?"Unmute audio":"Mute audio");
        muteButton.setTextColor(muted?CORAL:MUTED);
        muteButton.setContentDescription(muted?"Audio muted. Tap to unmute all Tempo speech.":"Audio on. Tap to mute all Tempo speech.");
    }
    private void settingsSwitch(FormSheet f,String title,String detail,boolean checked,java.util.function.Consumer<Boolean> change) {
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16),dp(10),dp(16),dp(12));row.setBackground(surface(PANEL,14));
        android.widget.Switch toggle=new android.widget.Switch(this);
        toggle.setText(title);toggle.setTextSize(16);toggle.setTextColor(IVORY);
        toggle.setTypeface(getResources().getFont(R.font.geist_medium));toggle.setChecked(checked);
        toggle.setMinHeight(dp(48));toggle.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{CYAN,MUTED}));
        toggle.setOnCheckedChangeListener((v,on)->change.accept(on));row.addView(toggle);
        TextView description=label(detail,13,MUTED);description.setPadding(0,dp(4),0,0);row.addView(description);
        LinearLayout.LayoutParams params=match(-2);params.topMargin=dp(10);f.body.addView(row,params);
    }
    private void showSettings() {
        android.content.SharedPreferences p=AudioSettings.prefs(this);
        FormSheet f=new FormSheet(this,"Settings","Your preferences are saved automatically, including during a workout.");
        settingsSwitch(f,"Mute all audio","Silences every Tempo cue, including connection alerts. Training continues normally.",AudioSettings.muted(this),this::setMuted);
        settingsSwitch(f,"Stage announcements","Hear the next stage and its target BPM range.",AudioSettings.stages(this),on->p.edit().putBoolean("stage_audio",on).apply());
        settingsSwitch(f,"Zone guidance","Hear when to ease off or increase effort after a sustained deviation.",AudioSettings.zones(this),on->p.edit().putBoolean("zone_audio",on).apply());
        f.label("Minimum time between zone reminders");
        android.widget.RadioGroup intervals=new android.widget.RadioGroup(this);
        intervals.setOrientation(LinearLayout.HORIZONTAL);
        for(int seconds:new int[]{30,45,60}){
            android.widget.RadioButton choice=new android.widget.RadioButton(this);
            choice.setId(seconds);choice.setText(seconds+" sec");choice.setTextSize(13);choice.setTextColor(IVORY);
            choice.setMinHeight(dp(48));intervals.addView(choice,weighted(1,dp(48)));
        }
        intervals.check(AudioSettings.interval(this));
        intervals.setOnCheckedChangeListener((group,id)->p.edit().putInt("cue_seconds",id).apply());f.body.addView(intervals);
        f.label("Mute overrides both announcement settings. It does not change your phone’s volume or silence other apps.");
        f.item("Workout history","Review saved sessions",()->{f.dismiss();showHistory();});
        f.label("Tempo 0.9.0\nIndependent app. Not affiliated with or endorsed by WHOOP.");
        f.show();
    }
    private void showHistory() {
        FormSheet f=new FormSheet(this,"Workout history","Saved on this phone only. Pauses and missing sensor time are not counted as training.");
        try {
            org.json.JSONArray records=SessionStore.history(this);
            if(records.length()==0)f.label("Your completed and stopped sessions will appear here.");
            for(int i=0;i<records.length();i++){
                org.json.JSONObject record=records.getJSONObject(i);
                TrainingSession s=TrainingSession.restore(record.toString());
                String date=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT).format(new java.util.Date(s.started));
                f.item(s.plan.name,date+"\n"+formatTime(s.elapsed/1000)+" active · "+s.outcome,()->{f.dismiss();showSessionDetail(s);});
            }
        }catch(Exception e){f.label("History could not be read. Your saved data has not been changed.");}
        f.show();
    }
    private void showSessionDetail(TrainingSession s) {
        FormSheet f=new FormSheet(this,s.plan.name,s.outcome.equals("completed")?"Completed session":"Stopped session");
        f.label("Active time: "+formatTime(s.elapsed/1000)+"\nTime in target: "+formatTime(s.target/1000)
            +"\nAverage: "+s.average()+" bpm · Peak: "+s.peak+" bpm");
        f.label("Time in each zone");
        for(int i=0;i<5;i++)f.label("Zone "+(i+1)+" ("+s.zones[i][0]+"–"+s.zones[i][1]+" bpm): "+formatTime(s.zoneMillis[i]/1000));
        f.label("Planned versus actual stages");
        for(int i=0;i<s.plan.phases.length;i++){
            WorkoutPlan.Phase stage=s.plan.phases[i];
            f.label(stage.name+" · Zone "+stage.zone+"\nPlanned "+formatTime(stage.seconds)+" / Actual "+formatTime(s.stageMillis[i]/1000)
                +" / In target "+formatTime(s.stageTarget[i]/1000));
        }
        TextView error=f.error();
        f.action("Delete session",false,()->new AlertDialog.Builder(this).setTitle("Delete this session?")
            .setMessage("This removes only this history entry. Your saved workout template is unchanged.")
            .setPositiveButton("Delete",(d,w)->{try{SessionStore.delete(this,s.id);f.dismiss();showHistory();}catch(Exception e){error.setText("Could not delete session.");}})
            .setNegativeButton("Cancel",null).show());
        f.show();
    }
    private void beginBackgroundSession() {
        try {
            String address = getPreferences(MODE_PRIVATE).getString("last_whoop_address", null);
            if (address == null) { openSensor();return; }
            int[][] ranges=new int[5][2];for(int i=0;i<5;i++)ranges[i]=zoneRange(i+1);
            TrainingSession s=new TrainingSession(workouts[selectedWorkout], ranges, address, phaseIndex);
            launchSession(s, true);
        } catch (Exception e) { Toast.makeText(this, "Could not start workout: "+e.getMessage(), Toast.LENGTH_LONG).show(); }
    }
    private void launchSession(TrainingSession s, boolean fresh) throws Exception {
        if (WorkoutService.instance != null) return;
        SessionStore.checkpoint(this,s);
        stopScan();closeGatt();
        WorkoutService.latest=null;displayedSession=null;serviceStarting=true;
        startForegroundService(new android.content.Intent(this,WorkoutService.class)
            .putExtra("session",s.json().toString()).putExtra("new",fresh));
        handler.postDelayed(()->{if(WorkoutService.instance==null){serviceStarting=false;setConnection("Training service unavailable. Reopen Tempo to resume.",false);}},2000);
    }

    private void resetWorkout() {
        if (serviceStarting) return;
        if (WorkoutService.instance != null && WorkoutService.latest != null && !WorkoutService.latest.done) {
            new AlertDialog.Builder(this).setTitle("Stop and save this session?")
                .setMessage("Your progress will be saved to History as a stopped workout.")
                .setPositiveButton("Stop & save",(d,w)->{if(WorkoutService.instance!=null)WorkoutService.instance.finish();syncSession();})
                .setNegativeButton("Keep training",null).show();return;
        }
        WorkoutService.latest=null;displayedSession=null;
        sessionRunning = sessionPaused = false;
        phaseIndex = 0;
        targetMillis = heartRateSum = 0;
        heartRateSamples = peakHeartRate = 0;
        primaryButton.setText("Start workout");
        workoutButton.setEnabled(true);
        zonesButton.setEnabled(true);
        renderState();
    }

    private void jumpToStage(int index) {
        if (serviceStarting) return;
        if (WorkoutService.instance != null && WorkoutService.latest != null && !WorkoutService.latest.done) {
            WorkoutService.instance.jump(index);syncSession();return;
        }
        WorkoutPlan plan = workouts[selectedWorkout];
        if (index < 0 || index >= plan.phases.length) return;
        WorkoutService.latest=null;displayedSession=null;
        sessionRunning=sessionPaused=false;
        phaseIndex = index;
        long now = SystemClock.elapsedRealtime();
        phaseStartedAt = now;
        pausedAt = now;
        lastMetricAt = now;
        renderState();
    }

    private void finishWorkout() {
        if (WorkoutService.instance != null && WorkoutService.latest != null && !WorkoutService.latest.done) {
            WorkoutService.instance.finish();syncSession();return;
        }
        sessionRunning = sessionPaused = false;
        primaryButton.setText("Start again");
        workoutButton.setEnabled(true);
        zonesButton.setEnabled(true);
        announce("Workout complete");
        renderState();
        cueText.setText("Circuit complete");
    }

    private void renderState() {
        WorkoutPlan plan = workouts[selectedWorkout];
        WorkoutPlan.Phase phase = plan.phases[Math.min(phaseIndex, plan.phases.length - 1)];
        int[] range = zoneRange(phase.zone);
        long elapsed = sessionRunning
                ? Math.max(0, (sessionPaused ? pausedAt : SystemClock.elapsedRealtime()) - phaseStartedAt) : 0;
        int secondsLeft = sessionRunning ? Math.max(0, phase.seconds - (int) (elapsed / 1000)) : phase.seconds;
        long completed = 0;
        if (sessionRunning) {
            for (int i = 0; i < phaseIndex; i++) completed += plan.phases[i].seconds;
            completed += elapsed / 1000;
        }
        float progress = completed / (float) Math.max(1, plan.totalSeconds());
        String cue = sessionPaused ? "PAUSED" : coachingCue(range);
        boolean finished = phaseIndex >= plan.phases.length;
        previousStage.setEnabled(phaseIndex > 0);
        previousStage.setAlpha(phaseIndex > 0 ? 1f : .4f);
        nextStage.setText(phaseIndex == plan.phases.length - 1 && sessionRunning ? "Finish" : "Next");
        boolean canNext = !finished && (phaseIndex < plan.phases.length - 1 || sessionRunning);
        nextStage.setEnabled(canNext); nextStage.setAlpha(canNext ? 1f : .4f);
        if (!sessionRunning) cue = finished ? "COMPLETE" : currentBpm == 0 ? "CONNECT SENSOR" : "READY";
        if (finished) { progress = 1; secondsLeft = 0; }
        if (!sessionRunning) primaryButton.setText(currentBpm == 0 ? "Connect to start" : finished ? "Train again" : "Start workout");
        targetText.setText("Zone " + phase.zone + "   /   " + range[0] + "–" + range[1] + " bpm");
        countdownText.setText(formatTime(secondsLeft));
        cueText.setText(prettyCue(cue));
        cueText.setTextColor(cueColor(cue));
        int average = heartRateSamples == 0 ? 0 : (int) (heartRateSum / heartRateSamples);
        statsText.setText("In zone  " + formatTime(targetMillis / 1000)
                + "     Average  " + (average == 0 ? "—" : average)
                + "     Peak  " + (peakHeartRate == 0 ? "—" : peakHeartRate));
        inZoneValue.setText(formatTime(targetMillis / 1000));
        averageValue.setText(average == 0 ? "—" : String.valueOf(average));
        peakValue.setText(peakHeartRate == 0 ? "—" : String.valueOf(peakHeartRate));
        nextText.setText(finished ? "Session saved · select a stage to train again"
                : "Stage " + (phaseIndex + 1) + "/" + plan.phases.length + " · " + phase.name);
        String key = selectedWorkout + ":" + phaseIndex;
        if (!key.equals(stageKey)) {
        stageKey = key;
        stageList.removeAllViews();
        for (int i = 0; i < plan.phases.length; i++) {
            WorkoutPlan.Phase p = plan.phases[i];
            TextView stage = label((i == phaseIndex ? "●  " : "○  ") + p.name + "   ·   Z" + p.zone
                    + "   ·   " + formatTime(p.seconds), 13, i == phaseIndex ? CYAN : MUTED);
            final int destination = i;
            stage.setMinHeight(dp(48)); stage.setGravity(Gravity.CENTER_VERTICAL);
            stage.setPadding(dp(8), dp(6), dp(8), dp(6));
            stage.setBackground(surface(i == phaseIndex ? PANEL : NIGHT, 10));
            stage.setOnClickListener(v -> jumpToStage(destination));
            stage.setFocusable(true);
            stage.setContentDescription("Restart at stage " + (i+1) + ": " + p.name);
            stageList.addView(stage);
        }
        }
        int[][] instrumentZones = new int[5][2]; for (int i=0;i<5;i++) instrumentZones[i]=zoneRange(i+1);
        trainingView.setZones(instrumentZones);
        trainingView.setData(currentBpm, restingHr, maxHr, phase.zone, range[0], range[1],
                progress, cue, phase.name);
    }

    private String coachingCue(int[] range) {
        if (currentBpm == 0) return "WAITING FOR HR";
        if (currentBpm < range[0]) return "PUSH";
        if (currentBpm > range[1]) return "EASE";
        return "HOLD";
    }

    private String prettyCue(String cue) {
        if ("WAITING FOR HR".equals(cue)) return "Waiting for heart rate";
        if ("CONNECT SENSOR".equals(cue)) return "Connect your sensor to begin";
        if ("COMPLETE".equals(cue)) return "Workout complete";
        if ("PUSH".equals(cue)) return "Below target · increase effort gradually";
        if ("EASE".equals(cue)) return "Above target · ease your pace";
        if ("HOLD".equals(cue)) return "On target · hold this effort";
        return cue.substring(0, 1) + cue.substring(1).toLowerCase(Locale.US);
    }

    private int cueColor(String cue) {
        if ("EASE".equals(cue)) return CORAL;
        if ("HOLD".equals(cue)) return AMBER;
        return CYAN;
    }

    private WorkoutPlan.Phase currentPhase() { return workouts[selectedWorkout].phases[phaseIndex]; }

    private int[] zoneRange(int zone) {
        if (displayedSession != null && sessionRunning) return displayedSession.zones[zone-1].clone();
        if (useCustomZones && customZones != null) return customZones[zone - 1].clone();
        int reserve = maxHr - restingHr;
        int low = Math.round(restingHr + reserve * (.4f + zone * .1f));
        int high = zone == 5 ? maxHr : Math.round(restingHr + reserve * (.5f + zone * .1f)) - 1;
        return new int[]{low, high};
    }

    private void announcePhase() {
        WorkoutPlan.Phase phase = currentPhase();
        int[] range = zoneRange(phase.zone);
        announce(phase.name + ". Zone " + phase.zone + " now. Target "
                + range[0] + " to " + range[1] + " beats per minute.");
    }

    private void announce(String message) {
        if (!AudioSettings.muted(this) && speechReady && textToSpeech != null)
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "training-cue");
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
            int result = textToSpeech.setLanguage(Locale.US);
            speechReady = result != TextToSpeech.LANG_MISSING_DATA
                    && result != TextToSpeech.LANG_NOT_SUPPORTED;
        }
    }

    private void ensurePermissionsAndScan() {
        if (bluetoothAdapter == null) return;
        String[] permissions = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(permissions, PERMISSION_REQUEST);
                return;
            }
        }
        startScan();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 43) {
            Toast.makeText(this, "Tap Start to begin. Notification controls require notification permission.", Toast.LENGTH_LONG).show();return;
        }
        if (requestCode != PERMISSION_REQUEST) return;
        for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) {
            setConnection("Bluetooth permission required", false);
            return;
        }
        startScan();
    }

    private TextView sensorStatus;
    private LinearLayout sensorResults;
    private long lastHrAt;
    private int connectionGeneration;
    private final Runnable staleCheck = new Runnable() {
        public void run() {
            if (destroyed) return;
            if (WorkoutService.instance == null && !serviceStarting && currentBpm > 0 && SystemClock.elapsedRealtime() - lastHrAt > 6000) {
                currentBpm = 0;
                setConnection("Signal lost · reconnect sensor", false);
                if (sessionRunning && !sessionPaused) toggleWorkout();
                renderState();
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void openSensor() {
        if (WorkoutService.instance != null && WorkoutService.latest != null && !WorkoutService.latest.done) {
            FormSheet f=new FormSheet(this,"Training sensor",WorkoutService.status);
            f.label("The workout reconnects automatically. Short signal drops pause the timer; after 30 seconds, tap Resume once readings return.");
            f.action("Retry connection",true,()->{if(WorkoutService.instance!=null)WorkoutService.instance.retry();f.dismiss();});
            f.show();return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(20), dp(24), dp(24));
        content.setBackgroundColor(PANEL);
        TextView heading = label("Connect your sensor", 24, IVORY);
        content.addView(heading);
        TextView help = label("In the WHOOP app, turn on Device Settings → Heart Rate Broadcast. Keep the band nearby, then select it below.", 15, MUTED);
        help.setPadding(0, dp(12), 0, dp(18));
        content.addView(help);
        sensorStatus = label("Ready to search", 14, CYAN);
        content.addView(sensorStatus);
        sensorResults = new LinearLayout(this);
        sensorResults.setOrientation(LinearLayout.VERTICAL);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(sensorResults);
        content.addView(scroll, match(dp(180)));
        Button search = button("Search again", true);
        search.setOnClickListener(v -> ensurePermissionsAndScan());
        content.addView(search, match(dp(48)));
        Button disconnect = button("Disconnect", false);
        disconnect.setOnClickListener(v -> {
            stopScan(); closeGatt(); currentBpm = 0;
            setConnection("Sensor disconnected", false); renderState();
        });
        content.addView(disconnect, match(dp(48)));
        TextView independence = label("Tempo is an independent app. Not affiliated with or endorsed by WHOOP.", 12, MUTED);
        independence.setPadding(0, dp(12), 0, 0);
        content.addView(independence);
        scanDialog = new AlertDialog.Builder(this).setView(content).create();
        scanDialog.setOnDismissListener(d -> { stopScan(); sensorStatus = null; sensorResults = null; });
        scanDialog.show();
        android.view.Window window = scanDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(-1, -2);
            window.setGravity(Gravity.BOTTOM);
        }
        if (currentBpm == 0) ensurePermissionsAndScan();
        else sensorStatus.setText("Receiving live heart rate");
    }

    private void startScan() {
        if (destroyed || bluetoothAdapter == null) return;
        if (sessionRunning && !sessionPaused) toggleWorkout();
        stopScan();
        closeGatt(); // A retry must release the previous GATT before accepting results.
        currentBpm = 0;
        devices.clear();
        if (sensorResults != null) sensorResults.removeAllViews();
        try {
            if (!bluetoothAdapter.isEnabled()) {
                setConnection("Turn on Bluetooth, then search again", false); return;
            }
            scanner = bluetoothAdapter.getBluetoothLeScanner();
            if (scanner == null) { setConnection("Bluetooth unavailable · retry", false); return; }
            scanning = true;
            setConnection("Searching for heart-rate sensors…", false);
            scanner.startScan(null, new android.bluetooth.le.ScanSettings.Builder()
                    .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
            handler.postDelayed(stopScanRunnable, SCAN_DURATION_MS);
        } catch (SecurityException | IllegalStateException e) {
            scanning = false;
            setConnection("Allow Nearby devices permission and retry", false);
        }
        renderState();
    }

    private void stopScan() {
        handler.removeCallbacks(stopScanRunnable);
        if (scanning && scanner != null) {
            try { scanner.stopScan(scanCallback); } catch (SecurityException | IllegalStateException ignored) { }
            scanning = false;
            if (gatt == null) setConnection(devices.isEmpty()
                    ? "No sensor found · enable HR Broadcast and retry"
                    : "Select your sensor below", false);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            runOnUiThread(() -> {
                if (!scanning || destroyed) return;
                BluetoothDevice device = result.getDevice();
                String name = safeDeviceName(device, result);
                List<ParcelUuid> services = result.getScanRecord() == null ? null : result.getScanRecord().getServiceUuids();
                if (!name.toUpperCase(Locale.US).contains("WHOOP")
                        && (services == null || !services.contains(new ParcelUuid(HEART_RATE_SERVICE)))) return;
                if (devices.put(device.getAddress(), device) != null) return;
                if (sensorResults != null) {
                    Button item = button(name + "\n" + device.getAddress(), false);
                    item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    item.setOnClickListener(v -> connect(device));
                    LinearLayout.LayoutParams p = match(dp(64)); p.topMargin = dp(8);
                    sensorResults.addView(item, p);
                }
                setConnection(devices.size() + " sensor found · tap to connect", false);
            });
        }
        @Override public void onScanFailed(int code) {
            runOnUiThread(() -> { stopScan(); setConnection("Search failed (" + code + ") · retry", false); });
        }
    };

    private void failConnection(String message) {
        if (sessionRunning && !sessionPaused) toggleWorkout();
        closeGatt(); currentBpm = 0;
        setConnection(message, false); renderState();
    }

    private void connect(BluetoothDevice device) {
        if (sessionRunning && !sessionPaused) toggleWorkout();
        stopScan(); closeGatt(); currentBpm = 0;
        int generation = connectionGeneration;
        setConnection("Connecting to " + safeDeviceName(device, null) + "…", false);
        try {
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) { failConnection("Connection could not start · retry"); return; }
            handler.postDelayed(() -> {
                if (!destroyed && generation == connectionGeneration && currentBpm == 0)
                    failConnection("No live HR received · check HR Broadcast and retry");
            }, 15000);
        } catch (SecurityException | IllegalArgumentException e) {
            failConnection("Connection unavailable · check Bluetooth permission");
        }
    }

    private void reconnectAutomatically() {
        handler.removeCallbacks(staleCheck);
        handler.post(staleCheck);
        if (WorkoutService.instance != null || serviceStarting) return;
        if (destroyed || bluetoothAdapter == null || !hasBluetoothPermission()) return;
        try {
            if (!bluetoothAdapter.isEnabled()) return;
            String address = getPreferences(MODE_PRIVATE).getString("last_whoop_address", null);
            if (address != null) connect(bluetoothAdapter.getRemoteDevice(address));
        } catch (SecurityException | IllegalArgumentException ignored) { }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt link, int status, int state) {
            runOnUiThread(() -> {
                if (destroyed || link != gatt) return;
                if (status != BluetoothGatt.GATT_SUCCESS || state == BluetoothProfile.STATE_DISCONNECTED) {
                    failConnection("Disconnected (" + status + ") · tap Sensor to reconnect");
                } else if (state == BluetoothProfile.STATE_CONNECTED) {
                    setConnection("Connected · reading heart-rate service…", false);
                    try {if (!link.discoverServices()) failConnection("Service discovery failed · retry");}
                    catch(SecurityException e){failConnection("Bluetooth permission required · reopen Sensor");}
                }
            });
        }
        @Override public void onServicesDiscovered(BluetoothGatt link, int status) {
            runOnUiThread(() -> {
                if (destroyed || link != gatt) return;
                try {
                BluetoothGattService service = status == 0 ? link.getService(HEART_RATE_SERVICE) : null;
                BluetoothGattCharacteristic hr = service == null ? null : service.getCharacteristic(HEART_RATE_MEASUREMENT);
                if (hr == null) { failConnection("HR Broadcast is unavailable on this sensor"); return; }
                BluetoothGattDescriptor descriptor = hr.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                if (descriptor == null || !link.setCharacteristicNotification(hr, true)) {
                    failConnection("Could not enable heart-rate updates"); return;
                }
                boolean notify = (hr.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
                byte[] enable = notify ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                boolean accepted;
                if (Build.VERSION.SDK_INT >= 33) accepted = link.writeDescriptor(descriptor, enable) == android.bluetooth.BluetoothStatusCodes.SUCCESS;
                else { descriptor.setValue(enable); accepted = link.writeDescriptor(descriptor); }
                if (!accepted) failConnection("Subscription could not start · retry");
                else setConnection("Enabling live heart rate…", false);
                } catch(SecurityException e){failConnection("Bluetooth permission required · reopen Sensor");}
            });
        }
        @Override public void onDescriptorWrite(BluetoothGatt link, BluetoothGattDescriptor descriptor, int status) {
            runOnUiThread(() -> {
                if (destroyed || link != gatt || !descriptor.getUuid().equals(CLIENT_CHARACTERISTIC_CONFIG)) return;
                if (status != 0) failConnection("Sensor rejected subscription (" + status + ")");
                else if (currentBpm == 0) setConnection("Subscribed · waiting for first heartbeat…", false);
            });
        }
        @Override public void onCharacteristicChanged(BluetoothGatt link, BluetoothGattCharacteristic c, byte[] value) {
            receive(link, c, value);
        }
        @Override public void onCharacteristicChanged(BluetoothGatt link, BluetoothGattCharacteristic c) {
            receive(link, c, c.getValue());
        }
    };

    private void receive(BluetoothGatt link, BluetoothGattCharacteristic c, byte[] value) {
        if (!HEART_RATE_MEASUREMENT.equals(c.getUuid()) || value == null) return;
        byte[] sample = value.clone();
        runOnUiThread(() -> {
            if (destroyed || link != gatt) return;
            int bpm = HeartRatePacket.parse(sample);
            if (bpm <= 0) return;
            currentBpm = bpm; lastHrAt = SystemClock.elapsedRealtime();
            getPreferences(MODE_PRIVATE).edit().putString("last_whoop_address", link.getDevice().getAddress()).apply();
            setConnection("Sensor connected · live " + bpm + " bpm", true);
            if (sensorStatus != null) sensorStatus.setText("Connected · receiving " + bpm + " bpm");
            renderState();
        });
    }

    private String safeDeviceName(BluetoothDevice device, ScanResult result) {
        String name = result == null || result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
        try {if ((name == null || name.trim().isEmpty()) && hasBluetoothPermission()) name = device.getName();}
        catch(SecurityException ignored){}
        return name == null || name.trim().isEmpty() ? "Heart-rate device" : name;
    }

    private void setConnection(String message, boolean connected) {
        runOnUiThread(() -> {
            if (sensorStatus != null) sensorStatus.setText(message);
            connectionText.setText(message);
            connectionText.setTextColor(connected ? CYAN : MUTED);
        });
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void closeGatt() {
        connectionGeneration++;
        BluetoothGatt old = gatt;
        gatt = null;
        if (old != null) {
            try { old.disconnect(); } catch (SecurityException ignored) { }
            try {old.close();} catch(SecurityException ignored){}
        }
    }

    @Override protected void onStop() {
        // Active training belongs to WorkoutService, not this Activity's lifecycle.
        handler.removeCallbacks(serviceRefresh);
        super.onStop();
    }

    @Override protected void onStart() {
        super.onStart();handler.removeCallbacks(serviceRefresh);handler.post(serviceRefresh);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        handler.removeCallbacks(stopScanRunnable);
        stopScan(); closeGatt();
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        super.onDestroy();
    }

    private Button button(String value, boolean selected) {
        Button button = new Button(this);
        button.setText(value); button.setTextSize(14); button.setAllCaps(false);
        button.setTypeface(getResources().getFont(R.font.geist_medium));
        button.setLetterSpacing(0);
        button.setPadding(dp(14), 0, dp(14), 0); button.setStateListAnimator(null);
        styleButton(button, selected);
        return button;
    }

    private void styleButton(Button button, boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(14));
        background.setColor(selected ? CYAN : PANEL);
        background.setStroke(dp(1), selected ? CYAN : Color.rgb(49, 68, 72));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(0x3066ecc4), background, null));
        button.setTextColor(selected ? NIGHT : IVORY);
        button.setMinHeight(0); button.setMinimumHeight(0);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setBackgroundTintList(null);
    }

    private TextView label(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        return view;
    }

    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); return row; }
    private LinearLayout.LayoutParams match(int height) { return new LinearLayout.LayoutParams(-1, height); }
    private LinearLayout.LayoutParams sized(int width, int height) { return new LinearLayout.LayoutParams(width, height); }
    private LinearLayout.LayoutParams weighted(float weight, int height) { return new LinearLayout.LayoutParams(0, height, weight); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String formatTime(long seconds) { return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60); }
    private static WorkoutPlan.Phase phase(String name, int zone, int seconds) { return new WorkoutPlan.Phase(name, zone, seconds); }
    private static UUID uuid16(String value) { return UUID.fromString("0000" + value + "-0000-1000-8000-00805f9b34fb"); }
}
