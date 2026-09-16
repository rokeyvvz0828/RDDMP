package com.ccb.development.service;

import com.ccb.development.config.DevelopmentSettings;
import com.ccb.security.model.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.ccb.development.model.DevelopmentTaskModels.*;

@Service
public class DevelopmentNumberService {
    private final JdbcTemplate jdbc;
    private final DevelopmentSettings settings;
    public DevelopmentNumberService(JdbcTemplate jdbc,DevelopmentSettings settings){this.jdbc=jdbc;this.settings=settings;}

    public void lockCreates(AuthUser actor){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("编号与任务必须处于同一事务");
        lock(actor,"CREATE_LOCK");
    }
    private long lock(AuthUser actor,String key){
        jdbc.update("INSERT INTO dev_task_number_sequence (tenant_id,sequence_key,next_value) VALUES (?,?,1) ON DUPLICATE KEY UPDATE next_value=next_value",actor.tenantId(),key);
        return jdbc.queryForObject("SELECT next_value FROM dev_task_number_sequence WHERE tenant_id=? AND sequence_key=? FOR UPDATE",Long.class,actor.tenantId(),key);
    }
    public String next(AuthUser actor,DevelopmentSourceResolver.ResolvedSource source){
        String date=LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key=source==null?"STANDALONE:"+date:"LEGACY:"+source.id();
        long ordinal=lock(actor,key);
        if(ordinal<=0||ordinal==Long.MAX_VALUE)throw conflict("编号序列已耗尽");
        String number=settings.numberingTemplate(actor,source!=null).replace("{date}",date)
                .replace("{requirementNo}",source==null?"":source.number()).replace("{seq}",String.format(Locale.ROOT,"%03d",ordinal));
        if(number.length()>255)throw conflict("生成编号过长，请调整编号模板");
        jdbc.update("UPDATE dev_task_number_sequence SET next_value=? WHERE tenant_id=? AND sequence_key=?",ordinal+1,actor.tenantId(),key);
        return number;
    }
}
