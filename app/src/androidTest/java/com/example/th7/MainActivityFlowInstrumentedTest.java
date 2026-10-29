package com.example.th7;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.os.SystemClock;
import android.os.Process;
import java.util.ArrayList;
import java.util.List;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.example.th7.data.BatteryRepository;
import com.example.th7.model.NotificationItem;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4.class)
public class MainActivityFlowInstrumentedTest {
    private Activity activity;
    private final List<String> testNotificationTitles = new ArrayList<>();
    private final List<String> testSessionIds = new ArrayList<>();
    @Test public void tabsCanNavigateToStatisticsAndCoach() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        onView(withId(R.id.nav_statistics)).perform(click());
        onView(withId(R.id.topToolbar)).check(matches(hasDescendant(withText(R.string.stats_title))));
        onView(withId(R.id.nav_coach)).perform(click());
        onView(withId(R.id.topToolbar)).check(matches(hasDescendant(withText(R.string.coach_toolbar_title))));
    }
    @After public void closeLaunchedActivity() {
        if (!testSessionIds.isEmpty()) {
            BatteryRepository repository = new BatteryRepository(InstrumentationRegistry.getInstrumentation().getTargetContext());
            try { for (String id : testSessionIds) repository.deleteSession(id); }
            finally { repository.close(); }
        }
        if (!testNotificationTitles.isEmpty()) {
            BatteryRepository repository = new BatteryRepository(InstrumentationRegistry.getInstrumentation().getTargetContext());
            try {
                for (NotificationItem item : repository.getNotifications())
                    if (testNotificationTitles.contains(item.getTitle())) repository.deleteNotification(item.getId());
            } finally {
                repository.close();
            }
        }
        if (activity != null && !activity.isFinishing()) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::finish);
        }
    }

    @Test public void chargingHistoryContextMenuFiltersStarAndDeleteWork() {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix = String.valueOf(Process.myPid());
        String recentType = "Wireless";
        int recentStart = 1 + (Process.myPid() % 70);
        int recentEnd = recentStart + 1;
        int oldStart = recentStart + 3;
        int oldEnd = oldStart + 2;
        String recentLabel = recentStart + "% → " + recentEnd + "% (+1%)";
        String oldLabel = oldStart + "% → " + oldEnd + "% (+2%)";
        String oldType = "USB";
        seedSession(context, recentType, System.currentTimeMillis() - 60_000L, recentStart, recentEnd);
        seedSession(context, oldType, System.currentTimeMillis() - 45L * 24 * 60 * 60 * 1000, oldStart, oldEnd);

        Intent intent = new Intent(context, ChargingHistoryActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        onView(withId(R.id.historyToolbar)).check(matches(isDisplayed()));
        waitForHistoryRows(activity, 2);
        onView(withText(recentLabel)).check(matches(isDisplayed()));
        onView(withText(oldLabel)).check(matches(isDisplayed()));
        onView(withText(recentLabel)).perform(click());
        onView(withText("Phiên sạc đã ghi nhận")).check(matches(isDisplayed()));
        onView(withText("Đóng")).perform(click());

        onView(withId(R.id.action_filter_time)).perform(click());
        onView(withText("24 giờ qua")).perform(click());
        onView(withText(recentLabel)).check(matches(isDisplayed()));
        onView(withText(oldLabel)).check(doesNotExist());
        onView(withId(R.id.action_filter_time)).perform(click());
        onView(withText("Toàn bộ thời gian")).perform(click());

        longClickHistoryItem(recentLabel);
        onView(withText(R.string.menu_star_item)).perform(click());
        onView(allOf(withId(R.id.ivStar), isDescendantOfA(historyItem(recentLabel))))
                .check(matches(isDisplayed()));
        SystemClock.sleep(300);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> activity.finish());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Intent reopen = new Intent(context, ChargingHistoryActivity.class);
        reopen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(reopen);
        waitForHistoryRows(activity, 2);
        onView(withText(recentLabel)).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.ivStar), isDescendantOfA(historyItem(recentLabel))))
                .check(matches(isDisplayed()));

        longClickHistoryItem(recentLabel);
        onView(withText(R.string.menu_unstar_item)).perform(click());
        onView(allOf(withId(R.id.ivStar), isDescendantOfA(historyItem(recentLabel))))
                .check(matches(not(isDisplayed())));
        if (true) onView(withId(R.id.chipHistWireless)).perform(click());
        else {
            onView(withId(R.id.action_filter_time)).perform(click());
            onView(withText("24 giờ qua")).perform(click());
        }
        longClickHistoryItem(recentLabel);
        onView(withText(R.string.menu_delete_item)).perform(click());
        onView(withText("Xóa phiên sạc?")).check(matches(isDisplayed()));
        onView(withText("Xóa")).perform(click());
        waitForSessionDeleted(context, testSessionIds.get(0));
        if (true) {
            onView(withId(R.id.recyclerChargingHistory)).check(matches(not(isDisplayed())));
            onView(withId(R.id.layoutEmptyHistory)).check(matches(isDisplayed()));
        }
        BatteryRepository repository = new BatteryRepository(context);
        try {
            boolean oldSessionRemains = false;
            for (com.example.th7.model.ChargingSession session : repository.getSessions()) {
                org.junit.Assert.assertNotEquals("deleted test session remains in SQLite", testSessionIds.get(0), session.getId());
                if (testSessionIds.get(1).equals(session.getId())) oldSessionRemains = true;
            }
            org.junit.Assert.assertTrue("test fixture cleanup target should still be present until @After", oldSessionRemains);
        } finally { repository.close(); }
    }

    @Test public void notificationReadPersistsAndSingleDeleteUpdatesUi() {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix = String.valueOf(Process.myPid());
        String lowTitle = "E2E read low battery " + suffix;
        String overheatTitle = "E2E delete overheat " + suffix;
        testNotificationTitles.add(lowTitle);
        testNotificationTitles.add(overheatTitle);
        BatteryRepository repository = new BatteryRepository(context);
        int baseCount;
        int baseUnread;
        try {
            List<NotificationItem> existing = repository.getNotifications();
            baseCount = existing.size();
            baseUnread = 0;
            for (NotificationItem item : existing) if (!item.isRead()) baseUnread++;
            repository.addNotification(lowTitle, "Read state UI test", "LOW_BATTERY", 0);
            repository.addNotification(overheatTitle, "Delete state UI test", "OVERHEAT", 0);
        } finally { repository.close(); }

        Intent intent = new Intent(context, NotificationCenterActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        onView(withId(R.id.tvInAppBadge)).check(matches(isDisplayed()));
        onView(withText(lowTitle)).perform(click());
        onView(withId(R.id.tvUnreadSummary)).check(matches(isDisplayed()));

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> activity.finish());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
        onView(withId(R.id.tvUnreadSummary)).check(matches(isDisplayed()));
        onView(allOf(withId(R.id.btnDeleteNotification), isDescendantOfA(notificationCard(overheatTitle))))
                .perform(click());
        onView(withText("Xóa thông báo?")).check(matches(isDisplayed()));
        onView(withText("Xóa")).perform(click());
        waitForNotificationDeleted(context, overheatTitle);
        onView(withText(overheatTitle)).check(doesNotExist());
        onView(withId(R.id.tvUnreadSummary)).check(matches(isDisplayed()));
        repository = new BatteryRepository(context);
        try {
            for (NotificationItem item : repository.getNotifications())
                if (lowTitle.equals(item.getTitle())) org.junit.Assert.assertTrue("read state was not saved", item.isRead());
                else org.junit.Assert.assertNotEquals("single-item delete was not saved", overheatTitle, item.getTitle());
        } finally { repository.close(); }
    }

    private void seedSession(android.content.Context context, String source, long start, int startLevel, int endLevel) {
        BatteryRepository repository = new BatteryRepository(context);
        try {
            ContentValues values = new ContentValues();
            values.put("start_ms", start);
            values.put("end_ms", start + 15 * 60_000L);
            values.put("start_level", startLevel);
            values.put("end_level", endLevel);
            values.put("source", source);
            values.put("starred", 0);
            values.put("end_observed", 1);
            long id = repository.getWritableDatabase().insertOrThrow("sessions", null, values);
            testSessionIds.add(String.valueOf(id));
        } finally { repository.close(); }
    }

    private void waitForHistoryRows(Activity target, int minimumRows) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            final int[] count = {0};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                androidx.recyclerview.widget.RecyclerView list = target.findViewById(R.id.recyclerChargingHistory);
                if (list != null) {
                    list.setItemAnimator(null);
                    if (list.getAdapter() != null) count[0] = list.getAdapter().getItemCount();
                }
            });
            if (count[0] >= minimumRows) return;
            SystemClock.sleep(50);
        }
        org.junit.Assert.fail("Timed out waiting for charging history rows");
    }

    private static org.hamcrest.Matcher<android.view.View> historyItem(String type) {
        return allOf(withId(R.id.cardChargingSession), hasDescendant(withText(type)));
    }
    private static void longClickHistoryItem(String type) { onView(historyItem(type)).perform(ViewActions.longClick()); }
    private static org.hamcrest.Matcher<android.view.View> notificationCard(String title) {
        return allOf(withId(R.id.cardNotification), hasDescendant(withText(title)));
    }
    private static void waitForSessionDeleted(android.content.Context context, String id) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            BatteryRepository repository = new BatteryRepository(context);
            boolean found = false;
            try { for (com.example.th7.model.ChargingSession session : repository.getSessions()) if (id.equals(session.getId())) found = true; }
            finally { repository.close(); }
            if (!found) return;
            SystemClock.sleep(100);
        }
        org.junit.Assert.fail("timed out waiting for test charging session deletion");
    }
    private static void waitForNotificationDeleted(android.content.Context context, String title) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            BatteryRepository repository = new BatteryRepository(context);
            boolean found = false;
            try { for (NotificationItem item : repository.getNotifications()) if (title.equals(item.getTitle())) found = true; }
            finally { repository.close(); }
            if (!found) return;
            SystemClock.sleep(100);
        }
        org.junit.Assert.fail("timed out waiting for test notification deletion");
    }

    @Test public void restoredTabRemainsActiveAfterActivityRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_statistics)).perform(click());
            onView(withId(R.id.mainTechGraph)).check(matches(isDisplayed()));
            scenario.recreate();
            onView(withId(R.id.topToolbar)).check(matches(hasDescendant(withText(R.string.stats_title))));
            onView(withId(R.id.mainTechGraph)).check(matches(isDisplayed()));
        }
    }

}
