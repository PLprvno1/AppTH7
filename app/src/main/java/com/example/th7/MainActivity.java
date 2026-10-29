package com.example.th7;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;
import android.os.Build;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.net.Uri;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.example.th7.data.AppSettings;
import com.example.th7.data.BatteryMonitor;
import com.example.th7.data.BatteryRepository;
import com.example.th7.data.BatteryStateMapper;
import com.example.th7.model.BatterySnapshot;
import com.example.th7.ui.BatteryCoachFragment;
import com.example.th7.ui.DashboardFragment;
import com.example.th7.ui.StatisticsFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final androidx.activity.result.ActivityResultLauncher<String> notificationPermission = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> { });
    private BottomNavigationView bottomNav; private MaterialToolbar toolbar; private boolean permissionPromptShown;
    private DashboardFragment dashboardFragment; private StatisticsFragment statisticsFragment; private BatteryCoachFragment batteryCoachFragment; private Fragment activeFragment;
    private BatteryRepository repository; private BatterySnapshot latestBattery; private boolean receiverRegistered, batteryRefreshInFlight;
    private final ExecutorService batteryIo=Executors.newSingleThreadExecutor();
    private final BroadcastReceiver batteryReceiver=new BroadcastReceiver(){@Override public void onReceive(android.content.Context context,Intent intent){
        BatterySnapshot snapshot=BatteryMonitor.read(MainActivity.this,intent);if(snapshot==null)return;latestBattery=snapshot;
        final boolean disconnected=intent!=null&&Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction());
        batteryIo.execute(()->{if(snapshot.level>=0)repository.recordSample(snapshot);if(disconnected)repository.finishSession(snapshot,true);else if(snapshot.isPlugged()){repository.startSession(snapshot);repository.updateOpenSession(snapshot);}else if(snapshot.statusType()!=BatteryStateMapper.Status.CHARGING)repository.recoverOpenSession(snapshot);
        });if(dashboardFragment!=null)dashboardFragment.render(snapshot);
    }};
    public BatterySnapshot getLatestBattery(){return latestBattery;}
    public void refreshBatteryState(java.util.function.Consumer<Boolean> callback){if(batteryRefreshInFlight){callback.accept(false);return;}batteryRefreshInFlight=true;batteryIo.execute(()->{try{BatterySnapshot fresh=BatteryMonitor.read(getApplicationContext(),null);runOnUiThread(()->{batteryRefreshInFlight=false;if(fresh!=null){latestBattery=fresh;if(dashboardFragment!=null)dashboardFragment.render(fresh);}callback.accept(fresh!=null);});}catch(RuntimeException e){runOnUiThread(()->{batteryRefreshInFlight=false;callback.accept(false);});}});}
    @Override protected void onResume(){super.onResume();BatterySnapshot current=BatteryMonitor.read(this,null);if(current!=null){latestBattery=current;if(dashboardFragment!=null)dashboardFragment.render(current);}getWindow().getDecorView().post(this::maybeExplainNotificationPermission);}
    @Override protected void onCreate(Bundle state){super.onCreate(state);EdgeToEdge.enable(this);setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main),(v,insets)->{Insets i=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(i.left,i.top,i.right,0);return insets;});
        toolbar=findViewById(R.id.topToolbar);bottomNav=findViewById(R.id.bottom_navigation);repository=new BatteryRepository(this);latestBattery=BatteryMonitor.read(this,null);setupToolbar();setupFragments(state);setupBottomNav();if(state!=null){bottomNav.setSelectedItemId(activeFragment==statisticsFragment?R.id.nav_statistics:activeFragment==batteryCoachFragment?R.id.nav_coach:R.id.nav_dashboard);}
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){if(bottomNav.getSelectedItemId()!=R.id.nav_dashboard)navigateToTab(R.id.nav_dashboard);else{setEnabled(false);getOnBackPressedDispatcher().onBackPressed();}}});
    }
    @Override protected void onStart(){super.onStart();if(!receiverRegistered){if(latestBattery!=null&&!latestBattery.isPlugged())batteryIo.execute(()->repository.recoverOpenSession(latestBattery));IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_BATTERY_CHANGED);f.addAction(Intent.ACTION_POWER_CONNECTED);f.addAction(Intent.ACTION_POWER_DISCONNECTED);ContextCompat.registerReceiver(this,batteryReceiver,f,ContextCompat.RECEIVER_NOT_EXPORTED);receiverRegistered=true;}}
    @Override protected void onStop(){if(receiverRegistered){unregisterReceiver(batteryReceiver);receiverRegistered=false;}super.onStop();}
    @Override protected void onDestroy(){batteryIo.shutdown();super.onDestroy();}
    private void setupToolbar(){toolbar.setOnMenuItemClickListener(this::handleToolbarMenu);}
    private boolean handleToolbarMenu(MenuItem item){int id=item.getItemId();if(id==R.id.action_notifications){startActivity(new Intent(this,NotificationCenterActivity.class));return true;}if(id==R.id.action_settings){startActivity(new Intent(this,SettingsActivity.class));return true;}if(id==R.id.action_history){startActivity(new Intent(this,ChargingHistoryActivity.class));return true;}if(id==R.id.action_refresh){latestBattery=BatteryMonitor.read(this,null);if(latestBattery!=null&&dashboardFragment!=null)dashboardFragment.render(latestBattery);Toast.makeText(this,getString(R.string.menu_refresh_done),Toast.LENGTH_SHORT).show();return true;}if(id==R.id.theme_dark||id==R.id.theme_light){boolean dark=id==R.id.theme_dark;AppSettings.setThemeMode(this,dark?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_NO);AppCompatDelegate.setDefaultNightMode(dark?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_NO);return true;}if(id==R.id.theme_system){AppSettings.setThemeMode(this,AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);return true;}if(id==R.id.action_about){new AlertDialog.Builder(this).setTitle("Battery Smart Lab").setMessage(getString(R.string.menu_about_message)).setPositiveButton(getString(R.string.common_close),null).show();return true;}return false;}
    private void setupFragments(Bundle state) {
        FragmentManager fm = getSupportFragmentManager();
        if (state == null) {
            dashboardFragment = new DashboardFragment();
            statisticsFragment = new StatisticsFragment();
            batteryCoachFragment = new BatteryCoachFragment();
            fm.beginTransaction()
                    .add(R.id.fragment_container, batteryCoachFragment, "COACH").hide(batteryCoachFragment)
                    .add(R.id.fragment_container, statisticsFragment, "STATS").hide(statisticsFragment)
                    .add(R.id.fragment_container, dashboardFragment, "DASH")
                    .commit();
            activeFragment = dashboardFragment;
            return;
        }

        Fragment legacyLab = fm.findFragmentByTag("LAB");
        if (legacyLab != null) fm.beginTransaction().remove(legacyLab).commitNow();
        dashboardFragment = (DashboardFragment) fm.findFragmentByTag("DASH");
        statisticsFragment = (StatisticsFragment) fm.findFragmentByTag("STATS");
        batteryCoachFragment = (BatteryCoachFragment) fm.findFragmentByTag("COACH");
        activeFragment = null;
        for (Fragment fragment : fm.getFragments()) {
            if (fragment.getId() == R.id.fragment_container && !fragment.isHidden()) {
                activeFragment = fragment;
                break;
            }
        }
        if (activeFragment == null || activeFragment == legacyLab) {
            activeFragment = dashboardFragment;
            if (dashboardFragment != null) fm.beginTransaction().show(dashboardFragment).commitNow();
        }
        updateToolbarForFragment(activeFragment);
    }    private void setupBottomNav(){bottomNav.setOnItemSelectedListener(item->{int id=item.getItemId();if(id==R.id.nav_dashboard){switchFragment(dashboardFragment,getString(R.string.dashboard_title),getString(R.string.dashboard_subtitle));return true;}if(id==R.id.nav_statistics){switchFragment(statisticsFragment,getString(R.string.stats_title),getString(R.string.stats_subtitle));return true;}if(id==R.id.nav_coach){switchFragment(batteryCoachFragment,getString(R.string.coach_title),getString(R.string.coach_subtitle));return true;}return false;});}
    private void switchFragment(Fragment target,String title,String subtitle){toolbar.setTitle(target==batteryCoachFragment?getString(R.string.coach_toolbar_title):title);toolbar.setSubtitle(target==batteryCoachFragment?getString(R.string.coach_toolbar_subtitle):subtitle);if(target!=null&&target!=activeFragment){FragmentTransaction tx=getSupportFragmentManager().beginTransaction();if(AppSettings.animations(this))tx.setCustomAnimations(android.R.anim.fade_in,android.R.anim.fade_out);if(activeFragment!=null)tx.hide(activeFragment);tx.show(target).commit();activeFragment=target;}}
    private void updateToolbarForFragment(Fragment fragment){if(fragment==statisticsFragment){toolbar.setTitle(R.string.stats_title);toolbar.setSubtitle(R.string.stats_subtitle);}else if(fragment==batteryCoachFragment){toolbar.setTitle(R.string.coach_toolbar_title);toolbar.setSubtitle(R.string.coach_toolbar_subtitle);}else{toolbar.setTitle(R.string.dashboard_title);toolbar.setSubtitle(R.string.dashboard_subtitle);}}
    public void navigateToTab(int id){if(bottomNav!=null)bottomNav.setSelectedItemId(id);}
    private void maybeExplainNotificationPermission(){if(permissionPromptShown||Build.VERSION.SDK_INT<33||isFinishing()||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)return;android.content.SharedPreferences p=getSharedPreferences("notification_permission",MODE_PRIVATE);if(p.getBoolean("asked",false))return;permissionPromptShown=true;new AlertDialog.Builder(this).setTitle(R.string.notification_permission_title).setMessage(R.string.notification_permission_message).setPositiveButton(R.string.common_continue,(d,w)->{p.edit().putBoolean("asked",true).apply();notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);}).setNegativeButton(R.string.common_cancel,(d,w)->p.edit().putBoolean("asked",true).apply()).setOnCancelListener(d->p.edit().putBoolean("asked",true).apply()).show();}
}
