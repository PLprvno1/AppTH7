package com.example.th7.data;

public final class BatteryUnits {
    private BatteryUnits() { }
    public static int levelPercent(int level,int scale){if(level<0||scale<=0||level>scale)return -1;return Math.round(level*100f/scale);}
    public static Float voltageVolts(int millivolts){return millivolts>0&&millivolts<=20000?millivolts/1000f:null;}
    public static Float currentMilliamps(int microamps){if(microamps==Integer.MIN_VALUE)return null;float v=microamps/1000f;return Math.abs(v)<=50000f?v:null;}
}
