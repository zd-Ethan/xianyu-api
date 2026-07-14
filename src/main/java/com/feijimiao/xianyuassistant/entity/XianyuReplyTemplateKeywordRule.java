package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
@TableName("xianyu_reply_template_keyword_rule")
public class XianyuReplyTemplateKeywordRule {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;
    private String keyword;
    private Integer matchMode;
    private Integer isFallback;
    private String createTime;
}
