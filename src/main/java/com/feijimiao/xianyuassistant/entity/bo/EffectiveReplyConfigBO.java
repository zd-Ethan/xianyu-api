package com.feijimiao.xianyuassistant.entity.bo;

import lombok.Data;

@Data
public class EffectiveReplyConfigBO {
    private Long xianyuAccountId;
    private String xyGoodsId;

    private Integer xianyuAutoReplyOn;
    private Integer xianyuAutoReplyContextOn;
    private Integer xianyuKeywordReplyOn;
    private Integer humanInterventionOn;
    private Integer humanInterventionMinutes;
    private Integer firstReplyOn;
    private Integer firstReplySkipManualOn;
    private String firstReplyText;
    private String firstReplyImageUrl;
    private String fixedMaterial;
    private Integer ragDelaySeconds;

    private String aiReplySourceName;
    private String keywordReplySourceName;
    private String humanInterventionSourceName;
    private String firstReplySourceName;
    private String fixedMaterialSourceName;
    private String ragDelaySourceName;
}
