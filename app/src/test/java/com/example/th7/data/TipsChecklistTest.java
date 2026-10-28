package com.example.th7.data;

import com.example.th7.model.BatteryTip;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;

public class TipsChecklistTest {
    private BatteryTip tip(String id, boolean completed) {
        return new BatteryTip(id, id, "description", "unknown", "open", 0, completed, true, -1);
    }

    @Test public void countsOnlyCompletedTips() {
        assertEquals(1, TipsChecklist.completedCount(Arrays.asList(tip("done", true), tip("todo", false))));
        assertEquals(0, TipsChecklist.completedCount(null));
    }

    @Test public void filtersAllCompletedAndIncompleteWithoutChangingInput() {
        java.util.List<BatteryTip> tips = Arrays.asList(tip("done", true), tip("todo", false));
        assertEquals(2, TipsChecklist.filter(tips, TipsChecklist.Filter.ALL).size());
        assertEquals("done", TipsChecklist.filter(tips, TipsChecklist.Filter.COMPLETED).get(0).id);
        assertEquals("todo", TipsChecklist.filter(tips, TipsChecklist.Filter.INCOMPLETE).get(0).id);
        assertEquals(2, tips.size());
    }

    @Test public void emptyAndUnknownFilterAreSafe() {
        assertEquals(Collections.emptyList(), TipsChecklist.filter(null, TipsChecklist.Filter.ALL));
        assertEquals(1, TipsChecklist.filter(Collections.singletonList(tip("todo", false)), null).size());
    }
}
