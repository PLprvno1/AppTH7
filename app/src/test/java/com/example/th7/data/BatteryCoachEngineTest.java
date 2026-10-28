package com.example.th7.data;
import android.os.BatteryManager;
import com.example.th7.model.BatterySnapshot;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;
public class BatteryCoachEngineTest {
 @Test public void overheatAdviceUsesSystemHealthOnly(){BatterySnapshot s=new BatterySnapshot(70,3,0,null,null,null,null,BatteryManager.BATTERY_HEALTH_OVERHEAT,10);assertEquals("Trạng thái pin",BatteryCoachEngine.evaluate(s,Collections.emptyList()).get(0).getCategory());}
 @Test public void unknownHealthDoesNotCreateOverheatAdvice(){BatterySnapshot s=new BatterySnapshot(70,3,0,null,null,null,null,BatteryManager.BATTERY_HEALTH_UNKNOWN,10);assertFalse(hasTitle(BatteryCoachEngine.evaluate(s,Collections.emptyList()),"Android báo pin quá nhiệt"));}
 @Test public void lowBatteryProducesAdvice(){BatterySnapshot s=new BatterySnapshot(15,BatteryManager.BATTERY_STATUS_DISCHARGING,0,null,null,null,null,BatteryManager.BATTERY_HEALTH_GOOD,10);assertEquals("Pin yếu",BatteryCoachEngine.evaluate(s,Collections.emptyList()).get(0).getCategory());}
 @Test public void rapidDrainRequiresEnoughObservedSamples(){BatterySnapshot a=snapshot(90,0),b=snapshot(85,10*60*1000L),c=snapshot(78,20*60*1000L);assertFalse(hasTitle(BatteryCoachEngine.evaluate(c,java.util.Arrays.asList(a,c)),"Mức pin giảm nhanh"));assertTrue(hasTitle(BatteryCoachEngine.evaluate(c,java.util.Arrays.asList(a,b,c)),"Mức pin giảm nhanh"));}
 @Test public void observedHighChargeHistoryNeedsThreeSessions(){BatterySnapshot current=snapshot(70,1);com.example.th7.model.ChargingSession session=new com.example.th7.model.ChargingSession("1","x","x",20,98,"USB",1,2,true);assertFalse(hasTitle(BatteryCoachEngine.evaluate(current,Collections.emptyList(),java.util.Collections.singletonList(session)),"Nhiều phiên sạc đạt mức pin cao"));assertTrue(hasTitle(BatteryCoachEngine.evaluate(current,Collections.emptyList(),java.util.Arrays.asList(session,session,session)),"Nhiều phiên sạc đạt mức pin cao"));}
 @Test public void ordinaryHealthReturnsNonDiagnosticAdvice(){assertEquals("Không có cảnh báo hiện tại",BatteryCoachEngine.evaluate(snapshot(70,1),Collections.emptyList()).get(0).getTitle());}
 private BatterySnapshot snapshot(int level,long time){return new BatterySnapshot(level,BatteryManager.BATTERY_STATUS_DISCHARGING,0,null,null,null,null,BatteryManager.BATTERY_HEALTH_GOOD,time);}
 private boolean hasTitle(java.util.List<com.example.th7.model.AdviceItem> advice,String title){for(com.example.th7.model.AdviceItem item:advice)if(title.equals(item.getTitle()))return true;return false;}
}
