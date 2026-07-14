package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuReplyTemplate;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface XianyuReplyTemplateMapper extends BaseMapper<XianyuReplyTemplate> {

    @Select("<script>" +
            "SELECT * FROM xianyu_reply_template " +
            "<where>" +
            "<if test='accountId != null'> AND (xianyu_account_id IS NULL OR xianyu_account_id = #{accountId}) </if>" +
            "</where>" +
            "ORDER BY update_time DESC, id DESC" +
            "</script>")
    List<XianyuReplyTemplate> selectAvailable(@Param("accountId") Long accountId);

    @Select("SELECT COUNT(1) FROM xianyu_reply_template_binding WHERE template_id = #{templateId}")
    int countBindings(@Param("templateId") Long templateId);
}
