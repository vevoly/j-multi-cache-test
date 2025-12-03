package com.github.vevoly.jmulticache.test.entity.dto;

import io.github.vevoly.jmulticache.api.structure.JMultiCacheScorable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRank implements JMultiCacheScorable, Serializable {

    private Long userId;
    private Double score;

    @Override
    public String getCacheId() {
        return String.valueOf(userId);
    }

    @Override
    public Double getCacheScore() {
        return score;
    }

    @Override
    public void setCacheId(String id) {
        this.userId = Long.valueOf(id);
    }

    @Override
    public void setCacheScore(Double score) {
        this.score = score;
    }
}
