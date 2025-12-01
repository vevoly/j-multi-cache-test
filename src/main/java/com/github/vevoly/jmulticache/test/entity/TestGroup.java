package com.github.vevoly.jmulticache.test.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestGroup implements Serializable {
    private Long id;
    private String tenantId;
    private String name;
}
