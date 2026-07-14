package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("xianyu_delivery_template")
public class XianyuDeliveryTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long xianyuAccountId;

    private String name;

    private String description;

    private Integer enabled;

    private Integer deliveryMode;

    private String autoDeliveryContent;

    private String kamiConfigIds;

    private String kamiDeliveryTemplate;

    private String autoDeliveryImageUrl;

    private Integer autoConfirmShipment;

    private Integer multiQuantityDelivery;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime = LocalDateTime.now();

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime = LocalDateTime.now();
}
