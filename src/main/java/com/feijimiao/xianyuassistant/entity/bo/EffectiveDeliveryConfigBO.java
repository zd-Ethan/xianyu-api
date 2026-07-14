package com.feijimiao.xianyuassistant.entity.bo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EffectiveDeliveryConfigBO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long xianyuAccountId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long xianyuGoodsId;

    private String xyGoodsId;

    private Integer deliveryMode;

    private String skuId;

    private String skuName;

    private String autoDeliveryContent;

    private String kamiConfigIds;

    private String kamiDeliveryTemplate;

    private String autoDeliveryImageUrl;

    private Integer autoConfirmShipment;

    private Integer multiQuantityDelivery;

    private Integer ragDelaySeconds;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    private String templateName;

    private Integer templateEnabled;

    private String deliveryModeSourceName;

    private String contentSourceName;

    private String imageSourceName;

    private String autoConfirmSourceName;

    private String multiQuantitySourceName;

    private Boolean hasEffectiveConfig;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
