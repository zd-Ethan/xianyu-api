package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuReplyTemplateKeywordContent;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface XianyuReplyTemplateKeywordContentMapper extends BaseMapper<XianyuReplyTemplateKeywordContent> {

    @Select("SELECT * FROM xianyu_reply_template_keyword_content WHERE template_rule_id = #{templateRuleId} ORDER BY id ASC")
    List<XianyuReplyTemplateKeywordContent> selectByTemplateRuleId(@Param("templateRuleId") Long templateRuleId);

    @Delete("DELETE FROM xianyu_reply_template_keyword_content WHERE template_rule_id = #{templateRuleId}")
    int deleteByTemplateRuleId(@Param("templateRuleId") Long templateRuleId);
}
