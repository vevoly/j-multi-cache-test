package com.github.vevoly.jmulticache.test.service;

import com.github.vevoly.jmulticache.test.entity.TestGroup;
import org.springframework.stereotype.Service;

import java.util.List;

public interface TestGroupService {

    TestGroup getById(Long id);

    List<TestGroup> listByTenantId(String tenantId);

    TestGroup getByName(String name);

    List<TestGroup> list();
}
