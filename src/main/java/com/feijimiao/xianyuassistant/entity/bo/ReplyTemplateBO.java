package com.feijimiao.xianyuassistant.entity.bo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

@Data
public class ReplyTemplateBO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long xianyuAccountId;
    private String name;
    private String description;
    private Integer enabled;
    private String createTime;
    private String updateTime;
    private Integer bindingCount;
    private Integer firstReplyOn;
    private Integer firstReplySkipManualOn;
    private String firstReplyText;
    private String firstReplyImageUrl;
    private Integer xianyuAutoReplyOn;
    private Integer xianyuAutoReplyContextOn;
    private Integer xianyuKeywordReplyOn;
    private Integer humanInterventionOn;
    private Integer humanInterventionMinutes;
    private Integer ragDelaySeconds;
    private String fixedMaterial;
    private List<TemplateKeywordRuleBO> rules;

    @Data
    public static class TemplateKeywordRuleBO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long templateId;
        private String keyword;
        private Integer matchMode;
        private Integer isFallback;
        private List<TemplateKeywordContentBO> contents;
    }

    @Data
    public static class TemplateKeywordContentBO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long templateRuleId;
        private String replyText;
        private String replyImageUrl;
    }
}
