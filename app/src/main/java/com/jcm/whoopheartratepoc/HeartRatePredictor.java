package com.jcm.whoopheartratepoc;

/** Small rolling linear forecast. Times are monotonic milliseconds. */
final class HeartRatePredictor {
    static final int HORIZON_SECONDS=15;
    private static final int CAPACITY=24;
    private static final long WINDOW_MS=12000, MAX_GAP_MS=7000;
    private final long[] times=new long[CAPACITY];
    private final int[] values=new int[CAPACITY];
    private int count;

    static final class Forecast {
        final boolean valid;
        final int projected;
        final float bpmPerMinute;
        final float confidence;
        Forecast(boolean valid,int projected,float bpmPerMinute,float confidence){
            this.valid=valid;this.projected=projected;this.bpmPerMinute=bpmPerMinute;this.confidence=confidence;
        }
    }

    void reset(){count=0;}

    void add(int bpm,long now){
        if(bpm<=0)return;
        if(count>0 && (now<times[count-1] || now-times[count-1]>MAX_GAP_MS))reset();
        if(count>0 && now==times[count-1]){values[count-1]=bpm;return;}
        if(count==CAPACITY){
            System.arraycopy(times,1,times,0,CAPACITY-1);
            System.arraycopy(values,1,values,0,CAPACITY-1);count--;
        }
        times[count]=now;values[count]=bpm;count++;
    }

    Forecast forecast(long now){
        if(count<5 || now-times[count-1]>3000)return invalid();
        int first=0;while(first<count && now-times[first]>WINDOW_MS)first++;
        int n=count-first;if(n<5 || times[count-1]-times[first]<4000)return invalid();
        double meanX=0,meanY=0;
        for(int i=first;i<count;i++){meanX+=(times[i]-times[first])/1000d;meanY+=values[i];}
        meanX/=n;meanY/=n;
        double xx=0,xy=0;
        for(int i=first;i<count;i++){
            double x=(times[i]-times[first])/1000d-meanX,y=values[i]-meanY;xx+=x*x;xy+=x*y;
        }
        if(xx<=0)return invalid();
        double slope=xy/xx;
        if(Math.abs(slope)>3)return invalid(); // Reject implausible sensor jumps.
        double intercept=meanY-slope*meanX,sse=0;
        for(int i=first;i<count;i++){
            double x=(times[i]-times[first])/1000d;
            double error=values[i]-(intercept+slope*x);sse+=error*error;
        }
        double rmse=Math.sqrt(sse/n);
        if(rmse>4)return invalid();
        int projected=(int)Math.round(values[count-1]+slope*HORIZON_SECONDS);
        projected=Math.max(30,Math.min(240,projected));
        float confidence=(float)Math.max(0,Math.min(1,(times[count-1]-times[first])/10000d*(1-rmse/6d)));
        return new Forecast(true,projected,(float)(slope*60),confidence);
    }

    private static Forecast invalid(){return new Forecast(false,0,0,0);}
}
