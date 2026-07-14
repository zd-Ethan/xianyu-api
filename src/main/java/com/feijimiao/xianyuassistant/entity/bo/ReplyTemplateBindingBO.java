package com.feijimiao.xianyuassistant.entity.bo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ReplyTemplateBindingBO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long xianyuAccountId;
    private String xyGoodsId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;
    private String templateName;
    private Integer templateEnabled;
    private Integer sortOrder;
    private Integer enabled;
    private String createTime;
    private String updateTime;
}
