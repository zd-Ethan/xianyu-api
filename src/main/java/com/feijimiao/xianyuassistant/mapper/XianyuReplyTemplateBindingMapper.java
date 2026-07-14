package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuReplyTemplateBinding;
import com.feijimiao.xianyuassistant.entity.bo.ReplyTemplateBindingBO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface XianyuReplyTemplateBindingMapper extends BaseMapper<XianyuReplyTemplateBinding> {

    @Select("SELECT b.*, t.name AS template_name, t.enabled AS template_enabled " +
            "FROM xianyu_reply_template_binding b " +
            "LEFT JOIN xianyu_reply_template t ON t.id = b.template_id " +
            "WHERE b.xianyu_account_id = #{accountId} AND b.xy_goods_id = #{xyGoodsId} " +
            "ORDER BY b.sort_order ASC, b.id ASC")
    List<ReplyTemplateBindingBO> selectByGoods(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId);

    @Select("SELECT * FROM xianyu_reply_template_binding " +
            "WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{xyGoodsId} AND template_id = #{templateId} " +
            "LIMIT 1")
    XianyuReplyTemplateBinding selectByGoodsAndTemplate(
            @Param("accountId") Long accountId,
            @Param("xyGoodsId") String xyGoodsId,
            @Param("templateId") Long templateId);

    @Select("SELECT DISTINCT xy_goods_id FROM xianyu_reply_template_binding " +
            "WHERE xianyu_account_id = #{accountId} AND enabled = 1")
    List<String> selectGoodsIdsByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT COALESCE(MAX(sort_order), 0) FROM xianyu_reply_template_binding " +
            "WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{xyGoodsId}")
    int maxSortOrder(@Param("accountId") Long accountId, @Param("xyGoodsId") String xyGoodsId);

    @Delete("DELETE FROM xianyu_reply_template_binding WHERE template_id = #{templateId}")
    int deleteByTemplateId(@Param("templateId") Long templateId);
}
