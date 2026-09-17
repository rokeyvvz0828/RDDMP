package com.ccb.security.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JwtConfigMapper {
    String findValue(@Param("key") String key);
}

@org.springframework.context.annotation.Configuration
@org.mybatis.spring.annotation.MapperScan(basePackageClasses = JwtConfigMapper.class)
class JwtConfigMapperConfiguration {}
