package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuReplyTemplateKeywordRule;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface XianyuReplyTemplateKeywordRuleMapper extends BaseMapper<XianyuReplyTemplateKeywordRule> {

    @Select("SELECT * FROM xianyu_reply_template_keyword_rule WHERE template_id = #{templateId} ORDER BY is_fallback ASC, id ASC")
    List<XianyuReplyTemplateKeywordRule> selectByTemplateId(@Param("templateId") Long templateId);

    @Select("SELECT * FROM xianyu_reply_template_keyword_rule WHERE template_id = #{templateId} AND keyword = #{keyword} AND match_mode = #{matchMode} AND is_fallback = 0")
    XianyuReplyTemplateKeywordRule selectByKeyword(
            @Param("templateId") Long templateId,
            @Param("keyword") String keyword,
            @Param("matchMode") Integer matchMode);

    @Select("SELECT * FROM xianyu_reply_template_keyword_rule WHERE template_id = #{templateId} AND is_fallback = 1")
    XianyuReplyTemplateKeywordRule selectFallback(@Param("templateId") Long templateId);

    @Delete("DELETE FROM xianyu_reply_template_keyword_rule WHERE template_id = #{templateId}")
    int deleteByTemplateId(@Param("templateId") Long templateId);
}
