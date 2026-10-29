package com.example.th7.ui;

import android.content.Intent;
import android.provider.Settings;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.th7.ChargingHistoryActivity;
import com.example.th7.BatteryAnalysisActivity;
import com.example.th7.R;
import com.example.th7.adapter.AppUsageAdapter;
import com.example.th7.data.AppUsageReader;
import com.example.th7.data.BatteryRepository;
import com.example.th7.data.UsageComparisonCalculator;
import com.example.th7.model.BatterySnapshot;
import com.example.th7.widget.TechGraphView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.tabs.TabLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatisticsFragment extends Fragment {
    private TechGraphView graph; private TextView title,value;
    private TextView usageStatus, usageTotal, usageComparison; private ProgressBar usageProgress; private RecyclerView usageList;
    private SwipeRefreshLayout refresh; private int metric=0,rangeId=R.id.btnRange24h; private BatteryRepository repository;
    private TextView rangeCoverage;
    private final ExecutorService io=Executors.newSingleThreadExecutor(); private boolean usageLoading;
    private int usageRangeId=R.id.usageRangeToday, usageRequestGeneration, statsRequestGeneration;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater i,@Nullable ViewGroup c,@Nullable Bundle b){
        View content=i.inflate(R.layout.fragment_statistics,c,false); refresh=new SwipeRefreshLayout(requireContext());
        refresh.setColorSchemeResources(R.color.tech_cyan); refresh.addView(content); refresh.setOnRefreshListener(()->{update(true);loadUsage();}); return refresh;
    }
    @Override public void onViewCreated(@NonNull View v,@Nullable Bundle b){
        if(b!=null){rangeId=b.getInt("stats_range_id",rangeId);metric=b.getInt("stats_metric",metric);}
        graph=v.findViewById(R.id.mainTechGraph);title=v.findViewById(R.id.tvChartTitle);value=v.findViewById(R.id.tvCurrentMetricVal);repository=new BatteryRepository(requireContext());
        rangeCoverage=v.findViewById(R.id.statsRangeCoverage);
        MaterialButtonToggleGroup timeRange=v.findViewById(R.id.toggleTimeRange);
        if(rangeId!=R.id.btnRange7d&&rangeId!=R.id.btnRange30d)rangeId=R.id.btnRange24h;
        timeRange.addOnButtonCheckedListener((g,id,checked)->{if(checked){rangeId=id;update(false);}});
        timeRange.check(rangeId);
        usageStatus=v.findViewById(R.id.usageStatus); usageTotal=v.findViewById(R.id.usageTotal);
        usageComparison=v.findViewById(R.id.usageComparison);
        usageProgress=v.findViewById(R.id.usageProgress); usageList=v.findViewById(R.id.appUsageList);
        usageList.setLayoutManager(new LinearLayoutManager(requireContext()));
        ((MaterialButtonToggleGroup)v.findViewById(R.id.toggleUsageRange)).addOnButtonCheckedListener((g,id,checked)->{if(checked){usageRangeId=id;loadUsage();}});
        v.findViewById(R.id.btnUsageAccess).setOnClickListener(x->openUsageAccessSettings());
        v.findViewById(R.id.btnOpenBatteryAnalysis).setOnClickListener(x->
                startActivity(new Intent(requireContext(), BatteryAnalysisActivity.class)));
        TabLayout metrics=v.findViewById(R.id.tabLayoutMetrics);
        metric=Math.max(0,Math.min(metrics.getTabCount()-1,metric));
        metrics.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener(){public void onTabSelected(TabLayout.Tab t){metric=t.getPosition();update(false);}public void onTabUnselected(TabLayout.Tab t){}public void onTabReselected(TabLayout.Tab t){}});
        metrics.selectTab(metrics.getTabAt(metric));
        v.findViewById(R.id.btnViewAllHistory).setOnClickListener(x->startActivity(new Intent(getContext(),ChargingHistoryActivity.class)));update(false);loadUsage();
    }
    @Override public void onResume(){super.onResume();update(false);loadUsage();}
    @Override public void onSaveInstanceState(@NonNull Bundle out){out.putInt("stats_range_id",rangeId);out.putInt("stats_metric",metric);super.onSaveInstanceState(out);}

    private void openUsageAccessSettings(){
        Intent intent=new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        try{startActivity(intent);}catch(RuntimeException e){usageStatus.setText(R.string.usage_access_unavailable);usageStatus.setVisibility(View.VISIBLE);}
    }

    private void loadUsage(){
        if(getView()==null||io.isShutdown())return;
        int request=++usageRequestGeneration;
        if(!AppUsageReader.hasUsageAccess(requireContext())){
            usageLoading=false;usageProgress.setVisibility(View.GONE);usageList.setVisibility(View.GONE);usageTotal.setVisibility(View.GONE);
            usageComparison.setVisibility(View.GONE);
            usageStatus.setText(R.string.usage_access_required);usageStatus.setVisibility(View.VISIBLE);
            getView().findViewById(R.id.btnUsageAccess).setVisibility(View.VISIBLE);return;
        }
        getView().findViewById(R.id.btnUsageAccess).setVisibility(View.GONE);
        usageStatus.setText(R.string.usage_loading);usageStatus.setVisibility(View.VISIBLE);usageProgress.setVisibility(View.VISIBLE);
        usageList.setVisibility(View.GONE);usageTotal.setVisibility(View.GONE);usageComparison.setVisibility(View.GONE);usageLoading=true;
        long end=System.currentTimeMillis(),begin=usageRangeStart(usageRangeId,end);
        android.content.Context appContext=requireContext().getApplicationContext();
        io.execute(()->{
            try{
                List<AppUsageReader.AppUsageRecord> records=AppUsageReader.read(appContext,begin,end);
                long todayStart=usageDayStart(end);
                long todayTotal=AppUsageReader.readTotalForegroundTime(appContext,todayStart,end);
                ArrayList<Long> previousDays=new ArrayList<>(10);
                Calendar previousDay=Calendar.getInstance();previousDay.setTimeInMillis(todayStart);
                for(int dayIndex=0;dayIndex<10;dayIndex++){
                    previousDay.add(Calendar.DAY_OF_YEAR,-1);
                    long dayStart=previousDay.getTimeInMillis();
                    previousDay.add(Calendar.DAY_OF_YEAR,1);
                    long dayEnd=previousDay.getTimeInMillis();
                    previousDays.add(AppUsageReader.readTotalForegroundTime(appContext,dayStart,dayEnd));
                    previousDay.add(Calendar.DAY_OF_YEAR,-1);
                }
                long usageDifference=UsageComparisonCalculator.differenceFromAverage(todayTotal,previousDays,10);
                if(getActivity()!=null)getActivity().runOnUiThread(()->{
                    if(request!=usageRequestGeneration||getView()==null)return;
                    usageLoading=false;usageProgress.setVisibility(View.GONE);
                    usageComparison.setText(formatUsageComparison(usageDifference));
                    usageComparison.setVisibility(View.VISIBLE);
                    if(records.isEmpty()){
                        usageList.setVisibility(View.GONE);usageTotal.setVisibility(View.GONE);
                        usageStatus.setVisibility(View.GONE);return;
                    }
                    long total=0L;for(AppUsageReader.AppUsageRecord record:records)total+=record.getForegroundMs();
                    usageTotal.setText(getString(R.string.usage_total_label)+": "+formatUsageDuration(total));
                    usageTotal.setVisibility(View.VISIBLE);usageStatus.setVisibility(View.GONE);usageList.setAdapter(new AppUsageAdapter(records));usageList.setVisibility(View.VISIBLE);
                });
            }catch(RuntimeException e){
                if(getActivity()!=null)getActivity().runOnUiThread(()->{
                    if(request!=usageRequestGeneration||getView()==null)return;
                    usageLoading=false;usageProgress.setVisibility(View.GONE);usageList.setVisibility(View.GONE);usageTotal.setVisibility(View.GONE);
                    usageComparison.setVisibility(View.GONE);
                    usageStatus.setText(R.string.usage_read_error);usageStatus.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private long usageRangeStart(int selected,long end){
        Calendar day=Calendar.getInstance();day.setTimeInMillis(end);day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);
        if(selected==R.id.usageRange7Days)day.add(Calendar.DAY_OF_YEAR,-6);
        else if(selected==R.id.usageRange30Days)day.add(Calendar.DAY_OF_YEAR,-29);
        return day.getTimeInMillis();
    }

    private long usageDayStart(long timestamp){
        Calendar day=Calendar.getInstance();day.setTimeInMillis(timestamp);day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);
        return day.getTimeInMillis();
    }

    private String formatUsageComparison(long differenceMs){
        if(differenceMs==0L)return getString(R.string.usage_comparison_equal);
        long absolute=Math.abs(differenceMs);
        String duration=absolute<60000L?getString(R.string.usage_comparison_under_minute):formatUsageDuration(absolute);
        return getString(differenceMs>0?R.string.usage_comparison_more:R.string.usage_comparison_less,duration);
    }

    private String formatUsageDuration(long durationMs){
        long seconds=Math.max(0L,durationMs/1000L),hours=seconds/3600L,minutes=(seconds%3600L)/60L;
        if(hours>0)return getString(R.string.usage_total_format,hours,minutes);
        if(minutes>0)return getString(R.string.usage_total_minutes,minutes);
        return getString(R.string.usage_total_seconds,seconds);
    }
    private void update(boolean fromRefresh){
        if(repository==null||io.isShutdown()||getView()==null)return;
        final int request=++statsRequestGeneration;
        long now=System.currentTimeMillis(),span=rangeId==R.id.btnRange7d?7L:rangeId==R.id.btnRange30d?30L:1L,from=now-span*86400000L;
        final int chosen=metric,range=rangeId;
        io.execute(()->{
            try{
                List<BatterySnapshot> samples=repository.getSamplesSince(from);
                ArrayList<BatterySnapshot> valid=new ArrayList<>();float sum=0;int count=0;
                double voltageSum=0;int voltageCount=0;
                for(BatterySnapshot sample:samples){
                    if(sample.timestamp<from||sample.timestamp>now)continue;
                    if(sample.voltageV!=null&&Float.isFinite(sample.voltageV)&&sample.voltageV>=0&&sample.voltageV<=20){voltageSum+=sample.voltageV;voltageCount++;}
                    Float metricValue=chosen==0||chosen==2
                            ?(sample.level>=0&&sample.level<=100?(float)sample.level:null)
                            :validCurrent(sample.currentMa);
                    if(metricValue!=null){sum+=metricValue;count++;valid.add(sample);}
                }
                if(valid.size()>24&&chosen!=2){
                    ArrayList<BatterySnapshot> sampled=new ArrayList<>(24);
                    int last=valid.size()-1;
                    for(int i=0;i<24;i++)sampled.add(valid.get(Math.round(i*last/23f)));
                    valid=sampled;
                }
                ArrayList<BatterySnapshot> result=valid;float average=count==0?Float.NaN:sum/count;
                float averageVoltage=voltageCount==0?Float.NaN:(float)(voltageSum/voltageCount);int n=count;
                if(getActivity()!=null)getActivity().runOnUiThread(()->{
                    if(request!=statsRequestGeneration||getView()==null)return;
                    render(result,average,averageVoltage,n,chosen,range);
                    if(refresh!=null)refresh.setRefreshing(false);
                });
            }catch(RuntimeException e){
                if(getActivity()!=null)getActivity().runOnUiThread(()->{
                    if(request!=statsRequestGeneration||getView()==null)return;
                    if(refresh!=null)refresh.setRefreshing(false);
                    if(fromRefresh&&isAdded())Toast.makeText(requireContext(),R.string.refresh_failed,Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private Float validCurrent(Float x){return x!=null&&Float.isFinite(x)&&Math.abs(x)<=50000f?x:null;}

    private void render(List<BatterySnapshot> samples,float average,float averageVoltage,int count,int selected,int range){
        if(getView()==null)return;
        if(rangeCoverage!=null){
            int label=range==R.id.btnRange7d?R.string.range_7d:range==R.id.btnRange30d?R.string.range_30d:R.string.range_24h;
            String coverage = samples.isEmpty() ? getString(R.string.stats_range_no_samples)
                    : new SimpleDateFormat(range==R.id.btnRange24h?"HH:mm":"dd/MM HH:mm",Locale.getDefault())
                    .format(new Date(samples.get(0).timestamp)) + " – "
                    + new SimpleDateFormat(range==R.id.btnRange24h?"HH:mm":"dd/MM HH:mm",Locale.getDefault())
                    .format(new Date(samples.get(samples.size()-1).timestamp));
            rangeCoverage.setText(getString(R.string.stats_range_coverage,getString(label),count,coverage));
        }
        if(selected==2){
            renderBatteryDrop(samples,range);
            return;
        }
        String unit=selected==0?"%":"mA";
        title.setText(selected==0?R.string.stats_metric_level_title:R.string.stats_metric_current_title);
        float[] points=new float[samples.size()];String[] labels=new String[samples.size()];
        SimpleDateFormat fmt=new SimpleDateFormat(range==R.id.btnRange24h?"HH:mm":"dd/MM",Locale.getDefault());
        for(int j=0;j<samples.size();j++){
            BatterySnapshot sample=samples.get(j);points[j]=selected==0?sample.level:sample.currentMa;
            labels[j]=fmt.format(new Date(sample.timestamp));
        }
        graph.setData(points,labels,unit);
        if(count==0)value.setText(R.string.stats_no_matching_data);
        else if(selected==1){
            String voltage=Float.isFinite(averageVoltage)?getString(R.string.stats_voltage_average,averageVoltage):getString(R.string.stats_voltage_unavailable);
            value.setText(getString(R.string.stats_current_average,average,voltage,count));
        }else value.setText(getString(R.string.stats_metric_average_level,average,count));
    }

    private void renderBatteryDrop(List<BatterySnapshot> samples,int range){
        title.setText(R.string.stats_metric_drop_title);
        ArrayList<Float> drops=new ArrayList<>();
        ArrayList<String> labels=new ArrayList<>();
        SimpleDateFormat format=new SimpleDateFormat(range==R.id.btnRange24h?"HH:mm":"dd/MM",Locale.getDefault());
        int segmentStartLevel=-1,maximumSegmentDrop=0,intervalCount=0;
        BatterySnapshot previous=null;
        for(BatterySnapshot sample:samples){
            boolean discharging=sample.statusType()==com.example.th7.data.BatteryStateMapper.Status.DISCHARGING
                    &&!sample.isPlugged()&&sample.level>=0&&sample.level<=100;
            if(!discharging){previous=null;segmentStartLevel=-1;continue;}
            if(previous==null)segmentStartLevel=sample.level;
            if(previous!=null){
                long gap=sample.timestamp-previous.timestamp;
                if(gap>0L&&gap<=30L*60L*1000L){
                    intervalCount++;
                    if(sample.level>previous.level)segmentStartLevel=sample.level;
                }else{
                    segmentStartLevel=sample.level;
                }
            }
            int segmentDrop=Math.max(0,segmentStartLevel-sample.level);
            maximumSegmentDrop=Math.max(maximumSegmentDrop,segmentDrop);
            drops.add((float)segmentDrop);
            labels.add(format.format(new Date(sample.timestamp)));
            previous=sample;
        }
        int plottedCount=Math.min(120,drops.size());
        float[] points=new float[plottedCount];String[] timeLabels=new String[plottedCount];
        for(int i=0;i<plottedCount;i++){
            int sourceIndex=plottedCount==1?drops.size()-1:Math.round(i*(drops.size()-1)/(float)(plottedCount-1));
            points[i]=drops.get(sourceIndex);timeLabels[i]=labels.get(sourceIndex);
        }
        graph.setData(points,timeLabels,"đpt");
        if(intervalCount==0)value.setText(R.string.stats_metric_drop_no_intervals);
        else value.setText(getString(R.string.stats_metric_drop_summary,maximumSegmentDrop,intervalCount));
    }

    @Override public void onDestroy(){io.shutdown();super.onDestroy();}
}

