package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

@Data
public class KamiConfigReqDTO {

    private Long id;

    private Long xianyuAccountId;

    private String aliasName;

    private Integer alertEnabled;

    private Integer alertThresholdType;

    private Integer alertThresholdValue;

    private String alertEmail;
}
