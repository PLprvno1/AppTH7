package com.example.th7.model;

public class ChargingSession {
    private final String id,timeRange,duration,chargeType; private final int startPercent,endPercent; private final long startedAt,endedAt; private boolean starred; private final boolean endTimeObserved;
    public ChargingSession(String id,String timeRange,String duration,int startPercent,int endPercent,String chargeType){this(id,timeRange,duration,startPercent,endPercent,chargeType,0L,0L,true);}
    public ChargingSession(String id,String timeRange,String duration,int startPercent,int endPercent,String chargeType,long startedAt){this(id,timeRange,duration,startPercent,endPercent,chargeType,startedAt,0L,true);}
    public ChargingSession(String id,String timeRange,String duration,int startPercent,int endPercent,String chargeType,long startedAt,long endedAt){this(id,timeRange,duration,startPercent,endPercent,chargeType,startedAt,endedAt,true);}
    public ChargingSession(String id,String timeRange,String duration,int startPercent,int endPercent,String chargeType,long startedAt,long endedAt,boolean endTimeObserved){this.id=id;this.timeRange=timeRange;this.duration=duration;this.startPercent=startPercent;this.endPercent=endPercent;this.chargeType=chargeType;this.startedAt=startedAt;this.endedAt=endedAt;this.endTimeObserved=endTimeObserved;}
    public String getId(){return id;} public String getTimeRange(){return timeRange;} public String getDuration(){return duration;} public int getStartPercent(){return startPercent;} public int getEndPercent(){return endPercent;} public String getChargeType(){return chargeType;} public boolean isStarred(){return starred;} public void setStarred(boolean v){starred=v;} public long getStartedAt(){return startedAt;} public long getEndedAt(){return endedAt;} public boolean isEndTimeObserved(){return endTimeObserved;}
}
