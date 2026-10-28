package com.example.th7.model;
import android.os.BatteryManager;
import com.example.th7.data.BatteryStateMapper;
public final class BatterySnapshot {
 public final int level,status,plugged,health; public final Float voltageV,currentMa,averageCurrentMa; public final Long chargeCounterUah; public final long timestamp;
 public BatterySnapshot(int level,int status,int plugged,Float voltageV,Float currentMa,Float averageCurrentMa,Long chargeCounterUah,int health,long timestamp){this.level=level;this.status=status;this.plugged=plugged;this.voltageV=voltageV;this.currentMa=currentMa;this.averageCurrentMa=averageCurrentMa;this.chargeCounterUah=chargeCounterUah;this.health=health;this.timestamp=timestamp;}
 public BatteryStateMapper.Status statusType(){return BatteryStateMapper.status(status);}
 public BatteryStateMapper.Source sourceType(){return BatteryStateMapper.source(plugged);}
 public BatteryStateMapper.Health healthType(){return BatteryStateMapper.health(health);}
 public BatteryStateMapper.ChargePath chargePath(){return BatteryStateMapper.classify(statusType(),sourceType());}
 public boolean isCharging(){return statusType()==BatteryStateMapper.Status.CHARGING||statusType()==BatteryStateMapper.Status.FULL;}
 public boolean isPlugged(){return plugged!=0&&plugged!=Integer.MIN_VALUE;}
 public String statusLabel(){switch(statusType()){case CHARGING:return "Đang sạc";case DISCHARGING:return "Đang xả pin";case FULL:return "Đã sạc đầy";case NOT_CHARGING:return "Không sạc";default:return "Không xác định";}}
 public String sourceLabel(){switch(sourceType()){case NONE:return "Chưa kết nối bộ sạc";case AC:return "Nguồn điện AC";case USB:return "USB";case WIRELESS:return "Sạc không dây";case UNAVAILABLE:return "Không đọc được nguồn sạc";case UNKNOWN:return "Nguồn sạc chưa xác định";default:return "Nguồn điện khác";}}
}
