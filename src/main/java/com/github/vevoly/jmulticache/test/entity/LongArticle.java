package com.github.vevoly.jmulticache.test.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LongArticle {
    private Long id;
    private String title;
    private String content;
}
