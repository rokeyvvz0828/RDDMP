package com.ccb.development.service;

import com.ccb.development.config.DevelopmentSettings;
import com.ccb.development.config.DevelopmentSettings.CalendarDefinition;
import com.ccb.development.model.DevelopmentStageModels.DurationMetrics;
import com.ccb.development.model.DevelopmentTaskModels.*;
import com.ccb.development.model.DevelopmentWorkItemModels.*;
import com.ccb.development.repository.DevelopmentCalendarRepository;
import com.ccb.security.model.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.HexFormat;

import static com.ccb.development.model.DevelopmentTaskModels.*;

@Service
public class DevelopmentCalendarService {
    private static final ObjectMapper CANONICAL_JSON=new ObjectMapper().findAndRegisterModules();
    private final DevelopmentSettings settings;
    private final DevelopmentCalendarRepository snapshots;
    public DevelopmentCalendarService(DevelopmentSettings settings,DevelopmentCalendarRepository snapshots,ObjectMapper json){this.settings=settings;this.snapshots=snapshots;}

    public CalendarDefinition current(AuthUser actor){
        var calendar=settings.calendar(actor);if(calendar==null)throw conflict("工作日历配置不能为空");return canonical(calendar);
    }
    public CalendarDefinition forTask(AuthUser actor,TaskEntity task){
        if(task.status()==TaskStatus.COMPLETED)return snapshots.latest(actor.tenantId(),"TASK",task.id()).orElseThrow(()->conflict("已完成任务缺少日历快照"));
        return current(actor);
    }
    public CalendarDefinition forItem(AuthUser actor,WorkItemEntity item){
        if(item.status()==WorkItemStatus.DONE)return snapshots.latest(actor.tenantId(),"WORK_ITEM",item.id()).orElseThrow(()->conflict("已完成工作项缺少日历快照"));
        return current(actor);
    }
    public void freeze(AuthUser actor,long taskId,String objectType,long id,long version){
        var calendar=current(actor);snapshots.freeze(actor,taskId,objectType,id,version,digest(calendar),calendar);
    }
    public static long workdays(LocalDate start,LocalDate end,CalendarDefinition calendar){
        if(start==null||end==null)throw bad("计算工作日需要完整日期");dates(start,end);
        var weekdays=new HashSet<>(calendar.weekdays());long span=ChronoUnit.DAYS.between(start,end)+1;
        long weeks=span/7,days=weeks*weekdays.size();
        // 完整周直接计数，最多再检查六天，避免跨多年范围逐日遍历。
        LocalDate remaining=start.plusDays(weeks*7);
        for(int offset=0;offset<span%7;offset++)if(weekdays.contains(remaining.plusDays(offset).getDayOfWeek().getValue()))days++;
        for(var date:calendar.workingDates())if(!date.isBefore(start)&&!date.isAfter(end)&&!weekdays.contains(date.getDayOfWeek().getValue()))days++;
        for(var date:calendar.restDates())if(!date.isBefore(start)&&!date.isAfter(end)&&weekdays.contains(date.getDayOfWeek().getValue()))days--;
        return days;
    }
    public static DurationMetrics measure(LocalDate plannedStart,LocalDate plannedEnd,LocalDate actualStart,LocalDate actualEnd,CalendarDefinition calendar){
        dates(plannedStart,plannedEnd);dates(actualStart,actualEnd);
        Long planned=plannedStart==null||plannedEnd==null?null:workdays(plannedStart,plannedEnd,calendar);
        Long actual=actualStart==null||actualEnd==null?null:workdays(actualStart,actualEnd,calendar);
        boolean available=planned!=null&&planned>0&&actual!=null;
        BigDecimal variance=available?BigDecimal.valueOf(actual-planned).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(planned),2,RoundingMode.HALF_UP):null;
        return new DurationMetrics(available?"available":"unavailable",planned,actual,variance,digest(calendar),
                calendar.workingDates().isEmpty()&&calendar.restDates().isEmpty()?"基础工作周":"已维护日期覆盖的工作周");
    }
    private static CalendarDefinition canonical(CalendarDefinition calendar){return new CalendarDefinition(calendar.weekdays().stream().sorted().toList(),
            calendar.workingDates().stream().sorted().toList(),calendar.restDates().stream().sorted().toList());}
    private static String digest(CalendarDefinition calendar){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CANONICAL_JSON.writeValueAsBytes(canonical(calendar))));}
        catch(Exception error){throw new IllegalStateException("无法生成日历版本",error);}}
}
