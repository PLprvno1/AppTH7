package com.example.th7.data;

import com.example.th7.model.BatterySnapshot;
import com.example.th7.model.ChargingSession;
import java.util.ArrayList;
import java.util.List;

public final class BatteryStatisticsCalculator {
    private BatteryStatisticsCalculator() { }
    public static Summary calculate(List<BatterySnapshot> samples,List<ChargingSession> sessions,long from,long to) {
        Summary r=new Summary(); long sum=0;double volts=0;int voltN=0;
        for(BatterySnapshot s:samples){if(s.timestamp<from||s.timestamp>to)continue;if(s.level>=0&&s.level<=100){r.min=Math.min(r.min,s.level);r.max=Math.max(r.max,s.level);sum+=s.level;r.levelCount++;}if(valid(s.voltageV,0,20)){volts+=s.voltageV;voltN++;}}
        if(r.levelCount>0)r.average=(float)sum/r.levelCount;if(voltN>0)r.averageVoltage=(float)(volts/voltN);
        for(ChargingSession s:sessions)if(s.getStartedAt()>=from&&s.getStartedAt()<=to){r.sessionCount++;if(s.getEndedAt()>s.getStartedAt()&&s.isEndTimeObserved()){r.completedSessions++;r.durationMinutes+=(s.getEndedAt()-s.getStartedAt())/60000L;}}
        if(r.completedSessions>0)r.durationMinutes/=r.completedSessions;return r;
    }
    public static ArrayList<BatterySnapshot> filter(List<BatterySnapshot> samples,long from,long to){ArrayList<BatterySnapshot> out=new ArrayList<>();for(BatterySnapshot s:samples)if(s.timestamp>=from&&s.timestamp<=to)out.add(s);return out;}
    private static boolean valid(Float value,float min,float max){return value!=null&&Float.isFinite(value)&&value>=min&&value<=max;}
    public static final class Summary {public int min=101,max=-1,levelCount,sessionCount,completedSessions;public float average=Float.NaN,averageVoltage=Float.NaN;public long durationMinutes;public boolean hasLevel(){return levelCount>0;}}
}
