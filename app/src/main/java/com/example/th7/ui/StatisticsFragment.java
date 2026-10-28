package com.example.th7.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.th7.ChargingHistoryActivity;
import com.example.th7.R;
import com.example.th7.data.BatteryRepository;
import com.example.th7.data.BatteryStatisticsCalculator;
import com.example.th7.model.BatterySnapshot;
import com.example.th7.model.ChargingSession;
import com.example.th7.widget.TechGraphView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.tabs.TabLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatisticsFragment extends Fragment {
    private TechGraphView graph; private TextView title,value,rangeValue,sessionCount,avgLevel,avgVoltage;
    private SwipeRefreshLayout refresh; private int metric=0,rangeId=R.id.btnRange24h; private BatteryRepository repository;
    private final ExecutorService io=Executors.newSingleThreadExecutor(); private boolean loading;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater i,@Nullable ViewGroup c,@Nullable Bundle b){
        View content=i.inflate(R.layout.fragment_statistics,c,false); refresh=new SwipeRefreshLayout(requireContext());
        refresh.setColorSchemeResources(R.color.tech_cyan); refresh.addView(content); refresh.setOnRefreshListener(()->update(true)); return refresh;
    }
    @Override public void onViewCreated(@NonNull View v,@Nullable Bundle b){
        graph=v.findViewById(R.id.mainTechGraph);title=v.findViewById(R.id.tvChartTitle);value=v.findViewById(R.id.tvCurrentMetricVal);rangeValue=v.findViewById(R.id.tvScreenOnTime);sessionCount=v.findViewById(R.id.tvChargeCycles);avgLevel=v.findViewById(R.id.tvAvgLevel);avgVoltage=v.findViewById(R.id.tvAvgVoltage);repository=new BatteryRepository(requireContext());
        ((MaterialButtonToggleGroup)v.findViewById(R.id.toggleTimeRange)).addOnButtonCheckedListener((g,id,checked)->{if(checked){rangeId=id;update(false);}});
        ((TabLayout)v.findViewById(R.id.tabLayoutMetrics)).addOnTabSelectedListener(new TabLayout.OnTabSelectedListener(){public void onTabSelected(TabLayout.Tab t){metric=t.getPosition();update(false);}public void onTabUnselected(TabLayout.Tab t){}public void onTabReselected(TabLayout.Tab t){}});
        v.findViewById(R.id.btnViewAllHistory).setOnClickListener(x->startActivity(new Intent(getContext(),ChargingHistoryActivity.class)));update(false);
    }
    @Override public void onResume(){super.onResume();update(false);}
    private void update(boolean fromRefresh){if(repository==null||io.isShutdown()||getView()==null)return;if(loading){if(fromRefresh&&refresh!=null)refresh.setRefreshing(false);return;}loading=true;long now=System.currentTimeMillis(),span=rangeId==R.id.btnRange7d?7L:rangeId==R.id.btnRange30d?30L:1L,from=now-span*86400000L;final int chosen=metric;final int range=rangeId;
        io.execute(()->{try{List<BatterySnapshot> samples=repository.getSamplesSince(from);List<ChargingSession> sessions=repository.getSessionsSince(from);BatteryStatisticsCalculator.Summary summary=BatteryStatisticsCalculator.calculate(samples,sessions,from,now);ArrayList<BatterySnapshot> valid=new ArrayList<>();float sum=0;int count=0;for(BatterySnapshot s:samples){if(s.timestamp<from||s.timestamp>now)continue;Float m=chosen==0?(s.level>=0&&s.level<=100?(float)s.level:null):validCurrent(s.currentMa);if(m!=null){sum+=m;count++;valid.add(s);}}if(valid.size()>24)valid=new ArrayList<>(valid.subList(valid.size()-24,valid.size()));ArrayList<BatterySnapshot> result=valid;float average=count==0?Float.NaN:sum/count;int n=count;if(getActivity()!=null)getActivity().runOnUiThread(()->{loading=false;render(result,summary,average,n,chosen,range);if(refresh!=null)refresh.setRefreshing(false);});}catch(RuntimeException e){if(getActivity()!=null)getActivity().runOnUiThread(()->{loading=false;if(refresh!=null)refresh.setRefreshing(false);if(fromRefresh)Toast.makeText(requireContext(),R.string.refresh_failed,Toast.LENGTH_SHORT).show();});}});
    }
    private Float validCurrent(Float x){return x!=null&&Float.isFinite(x)&&Math.abs(x)<=50000f?x:null;}
    private void render(List<BatterySnapshot> samples,BatteryStatisticsCalculator.Summary s,float average,int count,int selected,int range){if(getView()==null)return;String unit=selected==0?"%":"mA";title.setText(selected==0?R.string.stats_metric_level_title:R.string.stats_metric_current_title);float[] points=new float[samples.size()];String[] labels=new String[samples.size()];SimpleDateFormat fmt=new SimpleDateFormat(range==R.id.btnRange24h?"HH:mm":"dd/MM",Locale.getDefault());for(int j=0;j<samples.size();j++){BatterySnapshot b=samples.get(j);points[j]=selected==0?b.level:b.currentMa;labels[j]=fmt.format(new Date(b.timestamp));}graph.setData(points,labels,unit);
        if(count==0)value.setText(R.string.stats_no_matching_data);else if(selected==1){String voltage=Float.isFinite(s.averageVoltage)?getString(R.string.stats_voltage_average,s.averageVoltage):getString(R.string.stats_voltage_unavailable);value.setText(getString(R.string.stats_current_average,average,voltage,count));}else value.setText(getString(R.string.stats_metric_average_level,average,count));
        rangeValue.setText(s.hasLevel()?s.min+"% - "+s.max+"%":getString(R.string.value_no_data));sessionCount.setText(s.sessionCount==0?getString(R.string.stats_session_count,0):s.completedSessions==0?getString(R.string.stats_sessions_without_duration,s.sessionCount):s.durationMinutes<1?getString(R.string.stats_sessions_average_short,s.sessionCount):getString(R.string.stats_sessions_average_duration,s.sessionCount,s.durationMinutes));avgLevel.setText(s.hasLevel()?String.format(Locale.getDefault(),"%.1f%%",s.average):getString(R.string.value_no_data));avgVoltage.setText(Float.isFinite(s.averageVoltage)?getString(R.string.stats_voltage_average,s.averageVoltage):getString(R.string.value_no_data));}
    @Override public void onDestroy(){io.shutdown();super.onDestroy();}
}

