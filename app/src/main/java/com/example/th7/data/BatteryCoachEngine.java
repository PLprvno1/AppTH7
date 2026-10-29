package com.example.th7.data;

import android.os.BatteryManager;
import com.example.th7.R;
import com.example.th7.model.AdviceItem;
import com.example.th7.model.BatteryAnalysisResult;
import com.example.th7.model.BatteryFinding;
import com.example.th7.model.BatteryRecommendation;
import com.example.th7.model.BatterySnapshot;
import com.example.th7.model.ChargingSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Offline battery analysis. Gemini may explain this result but never changes it. */
public final class BatteryCoachEngine {
    private static final Config DEFAULT_CONFIG = new Config(10, 20, 50, 3,
            15L * 60 * 1000, 2L * 60 * 60 * 1000, 15.0f, 3, 95);

    private BatteryCoachEngine() { }

    /** Thresholds are heuristics and are intentionally explicit/configurable. */
    public static final class Config {
        public final int veryLowPercent;
        public final int lowPercent;
        public final int mediumPercent;
        public final int minimumDrainSamples;
        public final long minimumDrainObservationMs;
        public final long maximumSampleGapMs;
        public final float fastDrainPercentPerHour;
        public final int repeatedHighChargeSessions;
        public final int highChargePercent;

        public Config(int veryLowPercent, int lowPercent, int mediumPercent,
                      int minimumDrainSamples, long minimumDrainObservationMs,
                      long maximumSampleGapMs, float fastDrainPercentPerHour,
                      int repeatedHighChargeSessions, int highChargePercent) {
            if (veryLowPercent < 0 || lowPercent < veryLowPercent || mediumPercent < lowPercent
                    || mediumPercent > 100 || minimumDrainSamples < 2
                    || minimumDrainObservationMs <= 0 || maximumSampleGapMs <= 0
                    || fastDrainPercentPerHour <= 0 || repeatedHighChargeSessions < 1
                    || highChargePercent < 0 || highChargePercent > 100) {
                throw new IllegalArgumentException("Invalid battery analysis thresholds");
            }
            this.veryLowPercent = veryLowPercent;
            this.lowPercent = lowPercent;
            this.mediumPercent = mediumPercent;
            this.minimumDrainSamples = minimumDrainSamples;
            this.minimumDrainObservationMs = minimumDrainObservationMs;
            this.maximumSampleGapMs = maximumSampleGapMs;
            this.fastDrainPercentPerHour = fastDrainPercentPerHour;
            this.repeatedHighChargeSessions = repeatedHighChargeSessions;
            this.highChargePercent = highChargePercent;
        }
    }

    public static BatteryAnalysisResult analyze(BatterySnapshot current,
                                                List<BatterySnapshot> samples,
                                                List<ChargingSession> sessions,
                                                Boolean powerSaveMode) {
        return analyze(current, samples, sessions, powerSaveMode, DEFAULT_CONFIG);
    }

    public static BatteryAnalysisResult analyze(BatterySnapshot current,
                                                List<BatterySnapshot> samples,
                                                List<ChargingSession> sessions,
                                                Boolean powerSaveMode, Config config) {
        if (config == null) config = DEFAULT_CONFIG;
        long analyzedAt = System.currentTimeMillis();
        boolean hasLevel = current != null && current.level >= 0 && current.level <= 100;
        Integer level = hasLevel ? current.level : null;
        BatteryStateMapper.Status status = current == null
                ? BatteryStateMapper.Status.UNAVAILABLE : current.statusType();
        BatteryStateMapper.Source source = current == null
                ? BatteryStateMapper.Source.UNAVAILABLE : current.sourceType();
        BatteryStateMapper.Health health = current == null
                ? BatteryStateMapper.Health.UNAVAILABLE : current.healthType();

        BatteryAnalysisResult.LevelBand band = level == null
                ? BatteryAnalysisResult.LevelBand.UNKNOWN
                : level <= config.veryLowPercent ? BatteryAnalysisResult.LevelBand.VERY_LOW
                : level <= config.lowPercent ? BatteryAnalysisResult.LevelBand.LOW
                : level <= config.mediumPercent ? BatteryAnalysisResult.LevelBand.MEDIUM
                : BatteryAnalysisResult.LevelBand.HIGH;

        ArrayList<BatteryFinding> findings = new ArrayList<>();
        ArrayList<BatteryRecommendation> recommendations = new ArrayList<>();
        ArrayList<String> available = new ArrayList<>();
        ArrayList<String> unavailable = new ArrayList<>();

        if (hasLevel) available.add("Mức pin hiện tại");
        else unavailable.add("Mức pin hiện tại");
        if (isKnownStatus(status)) available.add("Trạng thái sạc");
        else unavailable.add("Trạng thái sạc");
        if (isKnownSource(source)) available.add("Nguồn sạc");
        else unavailable.add("Nguồn sạc");
        if (current != null && current.voltageV != null) available.add("Điện áp");
        else unavailable.add("Điện áp");
        if (current != null && current.currentMa != null) available.add("Dòng điện");
        else unavailable.add("Dòng điện");
        if (powerSaveMode == null) unavailable.add("Chế độ tiết kiệm pin");
        else available.add("Chế độ tiết kiệm pin");
        if (health == BatteryStateMapper.Health.UNAVAILABLE || health == BatteryStateMapper.Health.UNKNOWN
                || health == BatteryStateMapper.Health.UNRECOGNIZED) unavailable.add("Trạng thái pin do Android cung cấp");
        else available.add("Trạng thái pin do Android cung cấp");

        if (current == null || !hasLevel) {
            findings.add(finding("data_missing", "Chưa có dữ liệu pin", "Dữ liệu",
                    "Android chưa cung cấp mức pin hợp lệ.",
                    "Chưa thể đánh giá mức pin hiện tại.", BatteryFinding.Severity.INFO));
        } else {
            addLevelFinding(level, status, powerSaveMode, config, findings, recommendations);
        }
        if (current != null) {
            if (current.healthType() == BatteryStateMapper.Health.OVERHEAT) {
                findings.add(finding("health_overheat", "Android báo pin quá nhiệt", "Trạng thái pin",
                        "Android báo trạng thái quá nhiệt.",
                        "Đây là trạng thái hệ thống; không phải số đo nhiệt độ.",
                        BatteryFinding.Severity.WARNING));
                recommendations.add(recommendation("health_overheat", "Để thiết bị nguội",
                        "Ngừng sạc và để thiết bị ở nơi thoáng mát.", BatteryFinding.Severity.WARNING));
            } else if (current.healthType() == BatteryStateMapper.Health.DEAD) {
                findings.add(finding("health_dead", "Android báo lỗi pin", "Trạng thái pin",
                        "Android báo pin ở trạng thái lỗi.",
                        "Đây là trạng thái hệ thống, không phải chẩn đoán phần trăm sức khỏe.",
                        BatteryFinding.Severity.CRITICAL));
                recommendations.add(recommendation("health_dead", "Kiểm tra thiết bị",
                        "Android báo trạng thái pin nghiêm trọng. Hãy ngừng sạc nếu thấy dấu hiệu bất thường và liên hệ nơi sửa chữa.",
                        BatteryFinding.Severity.CRITICAL));
            } else if (current.healthType() == BatteryStateMapper.Health.OVER_VOLTAGE) {
                findings.add(finding("health_over_voltage", "Android báo điện áp pin cao", "Trạng thái pin",
                        "Android báo trạng thái quá áp.",
                        "Không thể xác định nguyên nhân chỉ từ trạng thái này.",
                        BatteryFinding.Severity.WARNING));
            } else if (current.healthType() == BatteryStateMapper.Health.UNSPECIFIED_FAILURE) {
                findings.add(finding("health_failure", "Android báo trạng thái pin bất thường", "Trạng thái pin",
                        "Android báo lỗi pin không xác định.",
                        "Thiết bị không cung cấp thêm thông tin về nguyên nhân.",
                        BatteryFinding.Severity.WARNING));
            }

        }

        ArrayList<BatterySnapshot> orderedSamples = normalizedSamples(current, samples, analyzedAt);
        boolean hasOldSamplesWithoutState = false;
        for (BatterySnapshot sample : orderedSamples) {
            if (sample.statusType() == BatteryStateMapper.Status.UNAVAILABLE
                    || sample.statusType() == BatteryStateMapper.Status.UNKNOWN
                    || sample.statusType() == BatteryStateMapper.Status.UNRECOGNIZED) {
                hasOldSamplesWithoutState = true;
                break;
            }
        }
        if (!orderedSamples.isEmpty()) available.add("Lịch sử mức pin");
        else unavailable.add("Lịch sử mức pin");
        if (hasOldSamplesWithoutState) unavailable.add("Trạng thái sạc chưa xác định ở một số mẫu");

        DrainObservation drain = findFastDrain(orderedSamples, config);
        int observedSamples = drain == null ? 0 : drain.sampleCount;
        long observedDuration = drain == null ? 0 : drain.durationMs;
        if (drain != null && drain.fast) {
            findings.add(finding("fast_drain", "Mức pin giảm nhanh", "Mức pin",
                    String.format(java.util.Locale.ROOT,
                            "Giảm %d điểm trong %d phút, qua %d lần ghi nhận (khoảng %.1f%%/giờ).",
                            drain.dropPercent, drain.durationMs / 60000L, drain.sampleCount,
                            drain.percentPerHour),
                    String.format(java.util.Locale.ROOT,
                            "Tốc độ vượt ngưỡng theo dõi %.0f%%/giờ trong một khoảng xả đủ dài.",
                            config.fastDrainPercentPerHour),
                    BatteryFinding.Severity.WARNING));
            recommendations.add(recommendation("fast_drain", "Theo dõi thêm",
                    "Kiểm tra mức pin trong lần sử dụng tiếp theo; chưa thể quy mức hao cho một ứng dụng cụ thể.",
                    BatteryFinding.Severity.ADVISORY));
        } else if (drain == null) {
            unavailable.add("Đủ mẫu xả pin để tính xu hướng");
            findings.add(finding("history_insufficient", "Chưa đủ dữ liệu hao pin", "Lịch sử",
                    "Cần ít nhất " + config.minimumDrainSamples + " mẫu xả pin trong "
                            + config.minimumDrainObservationMs / 60000L + " phút trở lên.",
                    "Không dùng các mẫu thiếu trạng thái sạc hoặc khoảng đo quá ngắn để kết luận.",
                    BatteryFinding.Severity.INFO));
        }

        int highEndSessions = 0;
        if (sessions != null) {
            for (ChargingSession session : sessions) {
                if (session != null && session.isEndTimeObserved()
                        && session.getEndPercent() >= config.highChargePercent) highEndSessions++;
            }
        }
        if (highEndSessions >= config.repeatedHighChargeSessions) {
            findings.add(finding("high_charge_sessions", "Nhiều phiên sạc đạt mức pin cao", "Lịch sử sạc",
                    highEndSessions + " phiên đã kết thúc ở mức " + config.highChargePercent + "% trở lên.",
                    "Đây chỉ là mô tả các phiên đã quan sát, không phải đánh giá sức khỏe pin.",
                    BatteryFinding.Severity.INFO));
        }

        if (findings.isEmpty()) {
            findings.add(finding("no_alert", "Không có cảnh báo hiện tại", "Trạng thái pin",
                    hasLevel ? "Mức pin hiện tại " + level + "%.": "Chưa có mức pin.",
                    "Kết luận chỉ dựa trên dữ liệu có sẵn.", BatteryFinding.Severity.INFO));
        }

        BatteryFinding.Severity overall = BatteryFinding.Severity.INFO;
        for (BatteryFinding item : findings) {
            if (item.severity.ordinal() > overall.ordinal()) overall = item.severity;
        }
        BatteryAnalysisResult.DataQuality quality;
        if (!hasLevel) quality = BatteryAnalysisResult.DataQuality.INSUFFICIENT;
        else if (!isKnownStatus(status) || drain == null) quality = BatteryAnalysisResult.DataQuality.LIMITED;
        else quality = BatteryAnalysisResult.DataQuality.SUFFICIENT;

        return new BatteryAnalysisResult(analyzedAt, level, band, status, source, health, powerSaveMode,
                current == null ? null : current.voltageV,
                current == null ? null : current.currentMa,
                overall, quality, findings, recommendations, available, unavailable,
                observedSamples, observedDuration);
    }

    private static void addLevelFinding(int level, BatteryStateMapper.Status status,
                                        Boolean powerSaveMode, Config config,
                                        List<BatteryFinding> findings,
                                        List<BatteryRecommendation> recommendations) {
        if (level > config.lowPercent) return;
        boolean veryLow = level <= config.veryLowPercent;
        String id = veryLow ? "very_low" : "low";
        String title = veryLow ? "Pin rất thấp" : "Pin thấp";
        findings.add(finding(id, title, "Pin yếu", "Mức pin hiện tại " + level + "%.",
                "Mức pin thấp không đồng nghĩa pin bị lỗi.", BatteryFinding.Severity.ADVISORY));
        boolean notCharging = status == BatteryStateMapper.Status.DISCHARGING
                || status == BatteryStateMapper.Status.NOT_CHARGING;
        if (veryLow && notCharging) {
            String message = Boolean.FALSE.equals(powerSaveMode)
                    ? "Kết nối sạc khi thuận tiện; có thể bật Tiết kiệm pin để kéo dài thời gian sử dụng."
                    : "Kết nối sạc khi thuận tiện.";
            recommendations.add(recommendation(id, "Ưu tiên sạc pin", message,
                    BatteryFinding.Severity.ADVISORY));
        } else if (notCharging && Boolean.FALSE.equals(powerSaveMode)) {
            recommendations.add(recommendation(id, "Bật Tiết kiệm pin nếu cần",
                    "Bạn có thể bật chế độ Tiết kiệm pin để giảm hoạt động nền.",
                    BatteryFinding.Severity.ADVISORY));
        } else if (notCharging && powerSaveMode == null) {
            recommendations.add(recommendation(id, "Cân nhắc Tiết kiệm pin",
                    "Mở Cài đặt nếu bạn muốn bật chế độ Tiết kiệm pin.",
                    BatteryFinding.Severity.ADVISORY));
        }
    }

    private static ArrayList<BatterySnapshot> normalizedSamples(BatterySnapshot current,
                                                                 List<BatterySnapshot> samples,
                                                                 long now) {
        ArrayList<BatterySnapshot> result = new ArrayList<>();
        if (samples != null) {
            for (BatterySnapshot sample : samples) {
                if (!isValidSample(sample, now)) continue;
                if (result.isEmpty() || sample.timestamp > result.get(result.size() - 1).timestamp) {
                    result.add(sample);
                }
            }
        }
        if (isValidSample(current, now)) {
            if (result.isEmpty() || current.timestamp > result.get(result.size() - 1).timestamp) {
                result.add(current);
            } else if (current.timestamp == result.get(result.size() - 1).timestamp) {
                result.set(result.size() - 1, current);
            }
        }
        return result;
    }

    private static boolean isValidSample(BatterySnapshot sample, long now) {
        return sample != null && sample.level >= 0 && sample.level <= 100
                && sample.timestamp >= 0 && sample.timestamp <= now + 5 * 60 * 1000L;
    }

    private static DrainObservation findFastDrain(List<BatterySnapshot> samples, Config config) {
        ArrayList<BatterySnapshot> segment = new ArrayList<>();
        DrainObservation latestWindow = null;
        DrainObservation latestFastWindow = null;
        for (BatterySnapshot sample : samples) {
            if (sample.statusType() != BatteryStateMapper.Status.DISCHARGING) {
                segment.clear();
                continue;
            }
            if (!segment.isEmpty()) {
                BatterySnapshot previous = segment.get(segment.size() - 1);
                long gap = sample.timestamp - previous.timestamp;
                if (gap <= 0 || gap > config.maximumSampleGapMs) segment.clear();
            }
            segment.add(sample);
            if (segment.size() < config.minimumDrainSamples) continue;
            BatterySnapshot first = segment.get(0), last = segment.get(segment.size() - 1);
            long duration = last.timestamp - first.timestamp;
            if (duration < config.minimumDrainObservationMs) continue;
            int drop = first.level - last.level;
            float rate = drop * 3600000f / duration;
            DrainObservation candidate = new DrainObservation(drop, rate, segment.size(), duration,
                    last.timestamp, drop > 0 && rate >= config.fastDrainPercentPerHour);
            if (latestWindow == null || last.timestamp > latestWindow.lastTimestamp) latestWindow = candidate;
            if (candidate.fast && (latestFastWindow == null
                    || last.timestamp > latestFastWindow.lastTimestamp)) latestFastWindow = candidate;
        }
        return latestFastWindow != null ? latestFastWindow : latestWindow;
    }

    private static boolean isKnownStatus(BatteryStateMapper.Status status) {
        return status == BatteryStateMapper.Status.CHARGING
                || status == BatteryStateMapper.Status.DISCHARGING
                || status == BatteryStateMapper.Status.NOT_CHARGING
                || status == BatteryStateMapper.Status.FULL;
    }

    private static boolean isKnownSource(BatteryStateMapper.Source source) {
        return source == BatteryStateMapper.Source.NONE || source == BatteryStateMapper.Source.AC
                || source == BatteryStateMapper.Source.USB || source == BatteryStateMapper.Source.WIRELESS
                || source == BatteryStateMapper.Source.OTHER;
    }

    private static BatteryFinding finding(String id, String title, String category,
                                          String evidence, String reason,
                                          BatteryFinding.Severity severity) {
        return new BatteryFinding(id, title, category, evidence, reason, severity);
    }

    private static BatteryRecommendation recommendation(String id, String title,
                                                         String message,
                                                         BatteryFinding.Severity priority) {
        return new BatteryRecommendation(id, title, message, priority);
    }

    /** Compatibility adapter for older UI/tests; all rules come from analyze(). */
    public static List<AdviceItem> evaluate(BatterySnapshot current, List<BatterySnapshot> samples) {
        return evaluate(current, samples, Collections.emptyList());
    }

    public static List<AdviceItem> evaluate(BatterySnapshot current, List<BatterySnapshot> samples,
                                            List<ChargingSession> sessions) {
        BatteryAnalysisResult result = analyze(current, samples, sessions, null);
        ArrayList<AdviceItem> advice = new ArrayList<>();
        for (BatteryFinding item : result.findings) {
            if ("history_insufficient".equals(item.id)) continue;
            String priority = item.severity == BatteryFinding.Severity.WARNING
                    || item.severity == BatteryFinding.Severity.CRITICAL ? "Cao"
                    : item.severity == BatteryFinding.Severity.ADVISORY ? "Trung bình" : "Thông tin";
            String message = item.evidence + " " + item.reason;
            for (BatteryRecommendation recommendation : result.recommendations) {
                if (item.id.equals(recommendation.id)) {
                    message = recommendation.message;
                    break;
                }
            }
            advice.add(new AdviceItem(item.id, item.title, message, item.category, priority,
                    iconFor(item.id)));
        }
        if (advice.isEmpty()) {
            advice.add(new AdviceItem("no_alert", "Không có cảnh báo hiện tại",
                    "Dựa trên dữ liệu pin hiện có.", "Trạng thái pin", "Thông tin",
                    R.drawable.ic_check_circle));
        }
        return advice;
    }

    private static int iconFor(String id) {
        if (id.startsWith("health_")) return R.drawable.ic_warning;
        if ("very_low".equals(id) || "low".equals(id)) return R.drawable.ic_power_save;
        if ("fast_drain".equals(id)) return R.drawable.ic_warning;
        if ("high_charge_sessions".equals(id)) return R.drawable.ic_battery_bolt;
        return R.drawable.ic_info;
    }

    private static final class DrainObservation {
        final int dropPercent;
        final float percentPerHour;
        final int sampleCount;
        final long durationMs;
        final long lastTimestamp;
        final boolean fast;

        DrainObservation(int dropPercent, float percentPerHour, int sampleCount,
                         long durationMs, long lastTimestamp, boolean fast) {
            this.dropPercent = dropPercent;
            this.percentPerHour = percentPerHour;
            this.sampleCount = sampleCount;
            this.durationMs = durationMs;
            this.lastTimestamp = lastTimestamp;
            this.fast = fast;
        }
    }
}
