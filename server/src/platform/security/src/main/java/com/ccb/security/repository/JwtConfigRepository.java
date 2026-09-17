package com.ccb.security.repository;

import org.springframework.stereotype.Repository;

@Repository
public class JwtConfigRepository {
    private final JwtConfigMapper mapper;
    public JwtConfigRepository(JwtConfigMapper mapper) { this.mapper = mapper; }
    public String findValue(String key) { return mapper.findValue(key); }
}
