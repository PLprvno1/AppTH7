package com.example.th7.data;

import com.example.th7.R;
import com.example.th7.model.AdviceItem;
import com.example.th7.model.BatterySnapshot;
import com.example.th7.model.ChargingSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Rule-based advice from Android battery state and observed history. */
public final class BatteryCoachEngine {
    private BatteryCoachEngine() { }
    public static List<AdviceItem> evaluate(BatterySnapshot current, List<BatterySnapshot> samples) {
        return evaluate(current, samples, Collections.emptyList());
    }
    public static List<AdviceItem> evaluate(BatterySnapshot current, List<BatterySnapshot> samples, List<ChargingSession> sessions) {
        ArrayList<AdviceItem> out = new ArrayList<>();
        if (current == null || current.level < 0) {
            out.add(new AdviceItem("data", "Chưa đủ dữ liệu pin", "Mở ứng dụng khi thiết bị hoạt động để đọc các chỉ số Android hỗ trợ.", "Dữ liệu", "Thông tin", R.drawable.ic_info));
            return out;
        }
        if (current.healthType() == BatteryStateMapper.Health.OVERHEAT) {
            out.add(new AdviceItem("overheat", "Android báo pin quá nhiệt", "Ngừng sạc, hạn chế sử dụng thiết bị và để máy nguội ở nơi thoáng mát.", "Trạng thái pin", "Cao", R.drawable.ic_warning));
        }
        if (current.level <= 20) out.add(new AdviceItem("low", "Mức pin thấp", "Pin còn không quá 20%. Bạn có thể bật chế độ tiết kiệm pin tích hợp trên thiết bị.", "Pin yếu", "Cao", R.drawable.ic_power_save));
        if (samples != null && samples.size() >= 3) {
            BatterySnapshot first = samples.get(0), last = samples.get(samples.size() - 1);
            long elapsed = last.timestamp - first.timestamp;
            if (elapsed >= 15 * 60 * 1000L && first.level - last.level >= 10)
                out.add(new AdviceItem("drain", "Mức pin giảm nhanh", "Đã ghi nhận mức pin giảm " + (first.level - last.level) + " điểm phần trăm trong " + (elapsed / 60000) + " phút.", "Tối ưu pin", "Trung bình", R.drawable.ic_warning));
        }
        int highEnd = 0;
        if (sessions != null) for (ChargingSession session : sessions)
            if (session.isEndTimeObserved() && session.getEndPercent() >= 95) highEnd++;
        if (highEnd >= 3) out.add(new AdviceItem("high_charge", "Nhiều phiên sạc đạt mức pin cao", "Trong " + highEnd + " phiên sạc đã ghi nhận, pin đạt ít nhất 95%. Thông tin này chỉ mô tả dữ liệu được theo dõi.", "Sạc pin", "Thông tin", R.drawable.ic_battery_bolt));
        if (out.isEmpty()) out.add(new AdviceItem("ok", "Không có cảnh báo hiện tại", "Đánh giá này dựa trên trạng thái pin Android và dữ liệu sẵn có; đây không phải phép đo sức khỏe pin.", "Tối ưu pin", "Thông tin", R.drawable.ic_check_circle));
        return out;
    }
}
