package com.ccb.testmanagement.service;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
class TestConfigurationRepository {
    private final TestConfigurationMapper mapper;

    TestConfigurationRepository(TestConfigurationMapper mapper) { this.mapper = mapper; }
    List<Map<String,Object>> projectOptions(Map<String,Object> p) { return mapper.projectOptions(p); }
    long physicalSystemCount(Map<String,Object> p) { return value(mapper.physicalSystemCount(p)); }
    List<Map<String,Object>> physicalSystemPage(Map<String,Object> p) { return mapper.physicalSystemPage(p); }
    Map<String,Object> participatingSystemId(Map<String,Object> p) { return mapper.participatingSystemId(p); }
    void insertParticipatingSystem(Map<String,Object> p) { mapper.insertParticipatingSystem(p); }
    void updateParticipatingSystem(Map<String,Object> p) { mapper.updateParticipatingSystem(p); }
    Map<String,Object> participatingSystem(Map<String,Object> p) { return mapper.participatingSystem(p); }
    long roleCount(Map<String,Object> p) { return value(mapper.roleCount(p)); }
    List<Map<String,Object>> rolePage(Map<String,Object> p) { return mapper.rolePage(p); }
    long roleExists(Map<String,Object> p) { return value(mapper.roleExists(p)); }
    void insertRole(Map<String,Object> p) { mapper.insertRole(p); }
    Map<String,Object> role(Map<String,Object> p) { return mapper.role(p); }
    void deleteRole(Map<String,Object> p) { mapper.deleteRole(p); }
    long projectExists(Map<String,Object> p) { return value(mapper.projectExists(p)); }
    long physicalExists(Map<String,Object> p) { return value(mapper.physicalExists(p)); }
    long enabledParticipatingSystemExists(Map<String,Object> p) { return value(mapper.enabledParticipatingSystemExists(p)); }
    long roundExists(Map<String,Object> p) { return value(mapper.roundExists(p)); }
    long cycleExists(Map<String,Object> p) { return value(mapper.cycleExists(p)); }
    long dictionaryExists(Map<String,Object> p) { return value(mapper.dictionaryExists(p)); }
    long optionExists(Map<String,Object> p) { return value(mapper.optionExists(p)); }
    long roundCount(Map<String,Object> p) { return value(mapper.roundCount(p)); }
    List<Map<String,Object>> roundPage(Map<String,Object> p) { return mapper.roundPage(p); }
    long roundUniqueCodeCount(Map<String,Object> p) { return value(mapper.roundUniqueCodeCount(p)); }
    long roundUniqueNameCount(Map<String,Object> p) { return value(mapper.roundUniqueNameCount(p)); }
    void insertRound(Map<String,Object> p) { mapper.insertRound(p); }
    void updateRound(Map<String,Object> p) { mapper.updateRound(p); }
    Map<String,Object> round(Map<String,Object> p) { return mapper.round(p); }
    long cycleReferenceCount(Map<String,Object> p) { return value(mapper.cycleReferenceCount(p)); }
    void deleteRound(Map<String,Object> p) { mapper.deleteRound(p); }
    long cycleCount(Map<String,Object> p) { return value(mapper.cycleCount(p)); }
    List<Map<String,Object>> cyclePage(Map<String,Object> p) { return mapper.cyclePage(p); }
    long cycleUniqueCodeCount(Map<String,Object> p) { return value(mapper.cycleUniqueCodeCount(p)); }
    long cycleUniqueNameCount(Map<String,Object> p) { return value(mapper.cycleUniqueNameCount(p)); }
    void insertCycle(Map<String,Object> p) { mapper.insertCycle(p); }
    void updateCycle(Map<String,Object> p) { mapper.updateCycle(p); }
    Map<String,Object> cycle(Map<String,Object> p) { return mapper.cycle(p); }
    void deleteCycle(Map<String,Object> p) { mapper.deleteCycle(p); }
    long dictionaryCount(Map<String,Object> p) { return value(mapper.dictionaryCount(p)); }
    List<Map<String,Object>> dictionaryPage(Map<String,Object> p) { return mapper.dictionaryPage(p); }
    long dictionaryUniqueCodeCount(Map<String,Object> p) { return value(mapper.dictionaryUniqueCodeCount(p)); }
    void insertDictionary(Map<String,Object> p) { mapper.insertDictionary(p); }
    void updateDictionary(Map<String,Object> p) { mapper.updateDictionary(p); }
    Map<String,Object> dictionary(Map<String,Object> p) { return mapper.dictionary(p); }
    long dictionaryOptionReferenceCount(Map<String,Object> p) { return value(mapper.dictionaryOptionReferenceCount(p)); }
    void deleteDictionary(Map<String,Object> p) { mapper.deleteDictionary(p); }
    long optionCount(Map<String,Object> p) { return value(mapper.optionCount(p)); }
    List<Map<String,Object>> optionPage(Map<String,Object> p) { return mapper.optionPage(p); }
    long optionUniqueCodeCount(Map<String,Object> p) { return value(mapper.optionUniqueCodeCount(p)); }
    void insertOption(Map<String,Object> p) { mapper.insertOption(p); }
    void updateOption(Map<String,Object> p) { mapper.updateOption(p); }
    Map<String,Object> option(Map<String,Object> p) { return mapper.option(p); }
    void deleteOption(Map<String,Object> p) { mapper.deleteOption(p); }
    Map<String,Object> roundDates(Map<String,Object> p) { return mapper.roundDates(p); }
    List<Map<String,Object>> dictionaryByCode(Map<String,Object> p) { return mapper.dictionaryByCode(p); }
    List<Map<String,Object>> physicalByCode(Map<String,Object> p) { return mapper.physicalByCode(p); }
    void insertAudit(Map<String,Object> p) { mapper.insertAudit(p); }
    private long value(Long value) { return value == null ? 0 : value; }
}
