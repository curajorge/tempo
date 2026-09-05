package com.jcm.whoopheartratepoc;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.ArrayList;

final class TrainingView extends View {
    static final int DRIVE = 0, REACTOR = 1;
    private final Paint p = new Paint(3);
    private final Typeface regularFont, numberFont;
    private int mode, bpm, rest = 60, max = 190, zone = 1, low, high;
    private float progress;
    private String cue = "READY", phase = "Warm up";
    private final ArrayList<Integer> history = new ArrayList<>();
    private long lastHistory;
    private int[][] zones;
    private boolean glass;
    void setGlass(boolean value) { glass = value; invalidate(); }
    void setZones(int[][] zones) { this.zones = zones; }
    private final int bg = Color.rgb(10,17,29), ink = Color.rgb(242,247,255),
            muted = Color.rgb(137,157,179), mint = Color.rgb(102,236,196);
    private final int[] colors = {0xff76a3d8,0xff66ecc4,0xffd5e780,0xffffb969,0xffff748a};
    TrainingView(Context c) {
        super(c);
        regularFont = c.getResources().getFont(R.font.geist_regular);
        numberFont = c.getResources().getFont(R.font.geist_medium);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void setMode(int v) { mode=v; invalidate(); }
    void setData(int bpm,int rest,int max,int zone,int low,int high,float progress,String cue,String phase) {
        this.bpm=bpm;this.rest=rest;this.max=max;this.zone=zone;this.low=low;this.high=high;
        this.progress=progress;this.cue=cue;this.phase=phase;
        long now=android.os.SystemClock.elapsedRealtime();
        if(now-lastHistory>=1000) { history.add(bpm); if(history.size()>60)history.remove(0); lastHistory=now; }
        setContentDescription((bpm>0?bpm+" beats per minute":"No live heart rate")+", target zone "+zone+", "+low+" to "+high);
        invalidate();
    }
    @Override protected void onDraw(Canvas c) {
        float scale=Math.min(getWidth()/360f,getHeight()/310f);
        if (!glass) c.drawColor(bg); c.save();
        c.translate((getWidth()-360*scale)/2,(getHeight()-310*scale)/2);c.scale(scale,scale);
        if (glass) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, 0, 360, 310, 0x554c6578, 0x22152536, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(2,2,358,308), 32,32,p);
            p.setShader(new RadialGradient(180,215,150,new int[]{0x403ed3ba,0x003ed3ba},null,Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(2,2,358,308),32,32,p);
            p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x507c9baa);
            c.drawRoundRect(new RectF(2,2,358,308),32,32,p);
        }
        if(mode==DRIVE) dial(c); else pulse(c);
        c.restore();
    }
    void dial(Canvas c) {
        float cx=180,cy=163,r=121;
        RectF ring=new RectF(cx-r,cy-r,cx+r,cy+r);
        for(int i=0;i<5;i++){
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.BUTT);p.setStrokeWidth(7);
            p.setColor(i+1==zone?colors[i]:Color.argb(95,Color.red(colors[i]),Color.green(colors[i]),Color.blue(colors[i])));
            c.drawArc(ring,140+i*52,46,false,p);
            double a=Math.toRadians(140+i*52+23);
            text(c,""+(i+1),cx+(float)Math.cos(a)*143,cy+(float)Math.sin(a)*143+4,11,i+1==zone?colors[i]:muted);
        }
        for(int i=0;i<=50 && !glass;i++){
            double a=Math.toRadians(140+i*5.2);
            float inner=i%5==0?99:105;
            p.setColor(i%5==0?0xff61768e:0xff2a3b51);p.setStrokeWidth(1);
            c.drawLine(cx+(float)Math.cos(a)*inner,cy+(float)Math.sin(a)*inner,
                    cx+(float)Math.cos(a)*110,cy+(float)Math.sin(a)*110,p);
        }
        if(bpm>0){
            float f = 0;
            if (zones != null) {
                for (int i=0;i<5;i++) {
                    if (bpm >= zones[i][0]) f = (i + Math.max(0, Math.min(1,
                            (bpm-zones[i][0])/(float)Math.max(1,zones[i][1]-zones[i][0]))))/5f;
                }
            } else f=Math.max(0,Math.min(1,((bpm-rest)/(float)(max-rest)-.5f)/.5f));
            double a=Math.toRadians(140+260*f);
            p.setStyle(Paint.Style.FILL);p.setColor(ink);
            float x=cx+(float)Math.cos(a)*121,y=cy+(float)Math.sin(a)*121;
            c.drawCircle(x,y,6,p);p.setColor(bg);c.drawCircle(x,y,2,p);
        }
        text(c,"Heart rate",180,117,13,muted);
        text(c,bpm==0?"—":""+bpm,180,182,64,ink);
        text(c,"bpm",180,209,14,muted);
        text(c,bpm==0?"Waiting for sensor":cue.equals("COMPLETE")?"Session complete":"Live from sensor",180,257,13,mint);
        legend(c);
    }
    void pulse(Canvas c) {
        text(c,"Heart rate",180,39,13,muted);
        text(c,bpm==0?"—":""+bpm,180,105,64,ink);
        text(c,"bpm",180,131,14,muted);
        RectF area=new RectF(16,157,344,257);
        p.setStyle(Paint.Style.FILL);p.setColor(0xff142b34);
        int graphLow = Math.min(rest, zones == null ? rest : zones[0][0]);
        int graphHigh = Math.max(max, zones == null ? max : zones[4][1]);
        float span=Math.max(1,graphHigh-graphLow);
        float top=area.bottom-(high-graphLow)/span*area.height();
        float bottom=area.bottom-(low-graphLow)/span*area.height();
        c.drawRect(area.left,top,area.right,bottom,p);
        p.setStyle(Paint.Style.STROKE);p.setColor(0xff25374b);p.setStrokeWidth(1);
        for(int i=0;i<=3;i++){float y=area.top+i*area.height()/3;c.drawLine(area.left,y,area.right,y,p);}
        Path path=new Path();boolean drawing=false;
        for(int i=0;i<history.size();i++){
            int hr=history.get(i);float x=area.left+i*area.width()/59;
            float y=area.bottom-Math.max(0,Math.min(1,(hr-graphLow)/span))*area.height();
            if(hr==0){drawing=false;continue;}
            if(!drawing)path.moveTo(x,y);else path.lineTo(x,y);drawing=true;
        }
        p.setColor(mint);p.setStrokeWidth(2.5f);c.drawPath(path,p);
        if(history.stream().noneMatch(v->v>0))text(c,"Your live trace will appear here",180,216,13,muted);
        text(c,"Last 60 seconds",180,278,11,muted);
        legend(c);
    }
    void legend(Canvas c){
        for(int i=0;i<5;i++){
            float x=38+i*71;p.setStyle(Paint.Style.FILL);p.setColor(colors[i]);c.drawCircle(x-12,301,3,p);
            text(c,"Z"+(i+1),x+4,305,11,i+1==zone?ink:muted);
        }
    }
    void text(Canvas c,String s,float x,float y,float size,int color){
        p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);
        p.setTypeface(size>40?numberFont:regularFont);
        p.setFontFeatureSettings("tnum");
        p.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,p);
    }
}
