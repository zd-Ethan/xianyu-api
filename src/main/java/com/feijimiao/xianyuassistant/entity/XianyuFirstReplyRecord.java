package com.feijimiao.xianyuassistant.entity;

import lombok.Data;

/**
 * 首次咨询回复记录。
 */
@Data
public class XianyuFirstReplyRecord {

    private Long id;

    private Long xianyuAccountId;

    private Long xianyuGoodsId;

    private String xyGoodsId;

    private String buyerUserId;

    private String buyerUserName;

    private String sId;

    private String pnmId;

    private String replyContent;

    private String replyImageUrl;

    private Integer state;

    private String createTime;

    private String updateTime;
}
