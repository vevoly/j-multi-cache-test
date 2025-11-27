package com.github.vevoly.jmulticache.test.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestUser implements Serializable {
    private Long id;
    private String tenantId;
    private String name;
    private Integer age;
}
