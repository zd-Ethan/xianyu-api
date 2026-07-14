package com.feijimiao.xianyuassistant.controller.dto;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import lombok.Data;

@Data
public class ItemWithConfigDTO {
    
    private XianyuGoodsInfo item;
    
    private Integer xianyuAutoDeliveryOn;
    
    private Integer xianyuAutoReplyOn;
    
    private Integer xianyuAutoReplyContextOn;
    
    private Integer xianyuKeywordReplyOn;

    private Integer humanInterventionOn;

    private Integer humanInterventionMinutes;

    private Integer firstReplyOn;

    private Integer firstReplySkipManualOn;

    private String firstReplyText;

    private String firstReplyImageUrl;

    private Integer effectiveXianyuAutoReplyOn;

    private Integer effectiveXianyuKeywordReplyOn;

    private Integer effectiveFirstReplyOn;

    private String effectiveFirstReplyText;

    private String effectiveFirstReplyImageUrl;
    
    private Integer autoDeliveryType;
    
    private String autoDeliveryContent;
}
