package com.feijimiao.xianyuassistant.entity.bo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

@Data
public class EffectiveKeywordRuleBO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceRuleId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;
    private String sourceType;
    private String sourceName;
    private Integer sourceOrder;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long xianyuAccountId;
    private String xyGoodsId;
    private String keyword;
    private Integer matchMode;
    private Integer isFallback;
    private String conflictKey;
    private Boolean overridden;
    private String overriddenBy;
    private List<KeywordReplyRuleBO.KeywordReplyContentBO> contents;
}
