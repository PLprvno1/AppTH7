package com.example.th7;

import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.NotificationManagerCompat;
import com.example.th7.data.AppSettings;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class SettingsActivity extends AppCompatActivity {
    private MaterialButtonToggleGroup themeModes;
    private MaterialSwitch animations, low, full, overheat;
    private boolean notificationNoticeShown;
    @Override protected void onCreate(Bundle state) {
        AppSettings.applyTheme(this); super.onCreate(state); setContentView(R.layout.activity_settings);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),(v,insets)->{androidx.core.graphics.Insets i=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());v.setPadding(i.left,i.top,i.right,0);return insets;});
        ((MaterialToolbar)findViewById(R.id.settingsToolbar)).setNavigationOnClickListener(v->finish());
        themeModes=findViewById(R.id.themeModeGroup); animations=findViewById(R.id.switchAnimations); low=findViewById(R.id.switchLowBattery); full=findViewById(R.id.switchFullCharge); overheat=findViewById(R.id.switchOverheat);
        int mode=AppSettings.getThemeMode(this);themeModes.check(mode==AppCompatDelegate.MODE_NIGHT_YES?R.id.themeModeDark:mode==AppCompatDelegate.MODE_NIGHT_NO?R.id.themeModeLight:R.id.themeModeSystem);
        animations.setChecked(AppSettings.animations(this));low.setChecked(AppSettings.lowAlert(this));full.setChecked(AppSettings.fullAlert(this));overheat.setChecked(AppSettings.overheatAlert(this));
        themeModes.addOnButtonCheckedListener((g,id,checked)->{if(!checked)return;int next=id==R.id.themeModeDark?AppCompatDelegate.MODE_NIGHT_YES:id==R.id.themeModeLight?AppCompatDelegate.MODE_NIGHT_NO:AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;AppSettings.setThemeMode(this,next);AppCompatDelegate.setDefaultNightMode(next);});
        animations.setOnCheckedChangeListener((b,c)->AppSettings.set(this,"animations",c));low.setOnCheckedChangeListener((b,c)->AppSettings.set(this,"low_alert",c));full.setOnCheckedChangeListener((b,c)->AppSettings.set(this,"full_alert",c));overheat.setOnCheckedChangeListener((b,c)->AppSettings.set(this,"overheat_alert",c));
        findViewById(R.id.rowLanguage).setEnabled(false);
        findViewById(R.id.rowLanguage).setOnClickListener(v->new AlertDialog.Builder(this).setMessage(R.string.language_dialog_message).setPositiveButton(R.string.common_close,null).show());
    }
    @Override protected void onResume(){super.onResume();if(!notificationNoticeShown&&Build.VERSION.SDK_INT>=33&&!NotificationManagerCompat.from(this).areNotificationsEnabled()){notificationNoticeShown=true;new AlertDialog.Builder(this).setTitle(R.string.notification_permission_title).setMessage(R.string.notification_permission_denied_message).setPositiveButton(R.string.notification_permission_open_settings,(d,w)->{android.content.Intent i=new android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());try{startActivity(i);}catch(RuntimeException e){Toast.makeText(this,R.string.tips_settings_unavailable,Toast.LENGTH_SHORT).show();}}).setNegativeButton(R.string.common_close,null).show();}}
}
