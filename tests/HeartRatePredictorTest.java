package com.jcm.whoopheartratepoc;

public final class HeartRatePredictorTest {
    private static int count;
    public static void main(String[] args){
        HeartRatePredictor rising=new HeartRatePredictor();
        for(int i=0;i<=6;i++)rising.add(120+i,i*1000L);
        HeartRatePredictor.Forecast up=rising.forecast(6000);
        ok(up.valid && up.projected==141 && up.bpmPerMinute>59 && up.bpmPerMinute<61,"rising forecast");

        HeartRatePredictor flat=new HeartRatePredictor();
        for(int i=0;i<=6;i++)flat.add(140,i*1000L);
        HeartRatePredictor.Forecast steady=flat.forecast(6000);
        ok(steady.valid && steady.projected==140 && Math.abs(steady.bpmPerMinute)<.01,"steady forecast");

        HeartRatePredictor falling=new HeartRatePredictor();
        for(int i=0;i<=6;i++)falling.add(160-i,i*1000L);
        HeartRatePredictor.Forecast down=falling.forecast(6000);
        ok(down.valid && down.projected==139 && down.bpmPerMinute<-59,"falling forecast");

        HeartRatePredictor sparse=new HeartRatePredictor();
        for(int i=0;i<4;i++)sparse.add(100+i,i*1000L);
        ok(!sparse.forecast(3000).valid,"needs enough samples");
        sparse.add(104,4000);ok(sparse.forecast(4000).valid,"accepts covered window");
        sparse.add(120,12000);ok(!sparse.forecast(12000).valid,"gap resets history");

        HeartRatePredictor noisy=new HeartRatePredictor();
        int[] noise={100,118,94,121,92,124};
        for(int i=0;i<noise.length;i++)noisy.add(noise[i],i*1000L);
        ok(!noisy.forecast(5000).valid,"rejects noisy sensor jumps");
        System.out.println(count+" heart-rate prediction tests passed");
    }
    private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);count++;}
}
