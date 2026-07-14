package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
@TableName("xianyu_reply_template")
public class XianyuReplyTemplate {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long xianyuAccountId;
    private String name;
    private String description;
    private Integer enabled;
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
    private String createTime;
    private String updateTime;
}
