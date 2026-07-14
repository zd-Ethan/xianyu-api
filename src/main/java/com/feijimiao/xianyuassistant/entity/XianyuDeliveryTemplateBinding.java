package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
@TableName("xianyu_delivery_template_binding")
public class XianyuDeliveryTemplateBinding {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long xianyuAccountId;

    private String xyGoodsId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    private Integer enabled;

    private String createTime;

    private String updateTime;
}
