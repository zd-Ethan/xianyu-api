package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 更新自动回复状态请求DTO
 */
@Data
public class UpdateAutoReplyReqDTO {
    
    /**
     * 闲鱼账号ID
     */
    private Long xianyuAccountId;
    
    /**
     * 闲鱼商品ID
     */
    private String xyGoodsId;
    
    /**
     * 自动回复开关：1-开启，0-关闭
     */
    private Integer xianyuAutoReplyOn;
    
    /**
     * 携带上下文开关：1-开启，0-关闭（可选，仅当xianyuAutoReplyOn=1时有意义）
     */
    private Integer xianyuAutoReplyContextOn;

    private Integer xianyuKeywordReplyOn;

    /**
     * 人工干预开关：1-开启，0-关闭
     */
    private Integer humanInterventionOn;

    private Integer humanInterventionMinutes;

    /**
     * 首次咨询回复开关：1-开启，0-关闭
     */
    private Integer firstReplyOn;

    private Integer firstReplySkipManualOn;

    /**
     * 首次咨询回复文本
     */
    private String firstReplyText;

    /**
     * 首次咨询回复图片URL
     */
    private String firstReplyImageUrl;
}
