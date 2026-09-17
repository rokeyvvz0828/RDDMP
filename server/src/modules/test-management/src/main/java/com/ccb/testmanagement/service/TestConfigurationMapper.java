package com.ccb.testmanagement.service;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
interface TestConfigurationMapper {
    List<Map<String, Object>> projectOptions(Map<String, Object> p);
    Long physicalSystemCount(Map<String, Object> p);
    List<Map<String, Object>> physicalSystemPage(Map<String, Object> p);
    Map<String, Object> participatingSystemId(Map<String, Object> p);
    int insertParticipatingSystem(Map<String, Object> p);
    int updateParticipatingSystem(Map<String, Object> p);
    Map<String, Object> participatingSystem(Map<String, Object> p);
    Long roleCount(Map<String, Object> p);
    List<Map<String, Object>> rolePage(Map<String, Object> p);
    Long roleExists(Map<String, Object> p);
    int insertRole(Map<String, Object> p);
    Map<String, Object> role(Map<String, Object> p);
    int deleteRole(Map<String, Object> p);
    Long projectExists(Map<String, Object> p);
    Long physicalExists(Map<String, Object> p);
    Long enabledParticipatingSystemExists(Map<String, Object> p);
    Long roundExists(Map<String, Object> p);
    Long cycleExists(Map<String, Object> p);
    Long dictionaryExists(Map<String, Object> p);
    Long optionExists(Map<String, Object> p);
    Long roundCount(Map<String, Object> p);
    List<Map<String, Object>> roundPage(Map<String, Object> p);
    Long roundUniqueCodeCount(Map<String, Object> p);
    Long roundUniqueNameCount(Map<String, Object> p);
    int insertRound(Map<String, Object> p);
    int updateRound(Map<String, Object> p);
    Map<String, Object> round(Map<String, Object> p);
    Long cycleReferenceCount(Map<String, Object> p);
    int deleteRound(Map<String, Object> p);
    Long cycleCount(Map<String, Object> p);
    List<Map<String, Object>> cyclePage(Map<String, Object> p);
    Long cycleUniqueCodeCount(Map<String, Object> p);
    Long cycleUniqueNameCount(Map<String, Object> p);
    int insertCycle(Map<String, Object> p);
    int updateCycle(Map<String, Object> p);
    Map<String, Object> cycle(Map<String, Object> p);
    int deleteCycle(Map<String, Object> p);
    Long dictionaryCount(Map<String, Object> p);
    List<Map<String, Object>> dictionaryPage(Map<String, Object> p);
    Long dictionaryUniqueCodeCount(Map<String, Object> p);
    int insertDictionary(Map<String, Object> p);
    int updateDictionary(Map<String, Object> p);
    Map<String, Object> dictionary(Map<String, Object> p);
    Long dictionaryOptionReferenceCount(Map<String, Object> p);
    int deleteDictionary(Map<String, Object> p);
    Long optionCount(Map<String, Object> p);
    List<Map<String, Object>> optionPage(Map<String, Object> p);
    Long optionUniqueCodeCount(Map<String, Object> p);
    int insertOption(Map<String, Object> p);
    int updateOption(Map<String, Object> p);
    Map<String, Object> option(Map<String, Object> p);
    int deleteOption(Map<String, Object> p);
    Map<String, Object> roundDates(Map<String, Object> p);
    List<Map<String, Object>> dictionaryByCode(Map<String, Object> p);
    List<Map<String, Object>> physicalByCode(Map<String, Object> p);
    int insertAudit(Map<String, Object> p);
}
