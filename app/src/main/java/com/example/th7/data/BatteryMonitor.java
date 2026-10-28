package com.example.th7.data;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.content.IntentFilter;
import com.example.th7.model.BatterySnapshot;
import com.example.th7.data.BatteryUnits;

public final class BatteryMonitor {
    private BatteryMonitor() { }
    public static BatterySnapshot read(Context context, Intent intent) {
        if(intent==null || !Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) intent=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(intent==null)return null;
        int level=intExtra(intent,BatteryManager.EXTRA_LEVEL), scale=intExtra(intent,BatteryManager.EXTRA_SCALE);
        int pct=BatteryUnits.levelPercent(level,scale);
        BatteryManager bm=(BatteryManager)context.getSystemService(Context.BATTERY_SERVICE);
        Float current=property(bm,BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        Float average=property(bm,BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        Long counter=null;
        if(bm!=null){long val=bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);if(val>0)counter=val;}
        int voltage=intExtra(intent,BatteryManager.EXTRA_VOLTAGE);
        return new BatterySnapshot(pct,intExtra(intent,BatteryManager.EXTRA_STATUS),intExtra(intent,BatteryManager.EXTRA_PLUGGED),voltage==Integer.MIN_VALUE?null:BatteryUnits.voltageVolts(voltage),current,average,counter,intExtra(intent,BatteryManager.EXTRA_HEALTH),System.currentTimeMillis());
    }
    private static int intExtra(Intent i,String key){return i.hasExtra(key)?i.getIntExtra(key,Integer.MIN_VALUE):Integer.MIN_VALUE;}
    private static Float property(BatteryManager bm,int property){if(bm==null)return null;int value=bm.getIntProperty(property);return BatteryUnits.currentMilliamps(value);}
}
