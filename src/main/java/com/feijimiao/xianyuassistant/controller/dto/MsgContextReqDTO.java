package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 查询上下文消息请求DTO
 */
@Data
public class MsgContextReqDTO {
    
    /**
     * 会话ID
     */
    private String sid;

    /**
     * 闲鱼账号ID（可选，用于全部账号视图下限定会话范围）
     */
    private Long xianyuAccountId;
    
    /**
     * 限制条数（默认20）
     */
    private Integer limit;
    
    /**
     * 偏移量（默认0）
     */
    private Integer offset;
}
