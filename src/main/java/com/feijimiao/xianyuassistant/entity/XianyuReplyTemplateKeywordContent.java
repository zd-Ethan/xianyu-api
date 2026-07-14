package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
@TableName("xianyu_reply_template_keyword_content")
public class XianyuReplyTemplateKeywordContent {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateRuleId;
    private String replyText;
    private String replyImageUrl;
    private String createTime;
}
