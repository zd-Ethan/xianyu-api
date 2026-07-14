package com.feijimiao.xianyuassistant.entity.bo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeliveryTemplateBO {

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

    private Integer bindingCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
