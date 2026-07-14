package com.feijimiao.xianyuassistant.mapper;

import com.feijimiao.xianyuassistant.entity.XianyuFirstReplyRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 首次咨询回复记录Mapper。
 */
@Mapper
public interface XianyuFirstReplyRecordMapper {

    @Insert("INSERT OR IGNORE INTO xianyu_first_reply_record " +
            "(xianyu_account_id, xianyu_goods_id, xy_goods_id, buyer_user_id, buyer_user_name, s_id, pnm_id, reply_content, reply_image_url, state) " +
            "VALUES (#{xianyuAccountId}, #{xianyuGoodsId}, #{xyGoodsId}, #{buyerUserId}, #{buyerUserName}, #{sId}, #{pnmId}, #{replyContent}, #{replyImageUrl}, #{state})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIgnore(XianyuFirstReplyRecord record);

    @Update("UPDATE xianyu_first_reply_record SET state = #{state}, reply_content = #{replyContent}, reply_image_url = #{replyImageUrl} WHERE id = #{id}")
    int updateState(@Param("id") Long id,
                    @Param("state") Integer state,
                    @Param("replyContent") String replyContent,
                    @Param("replyImageUrl") String replyImageUrl);

    @Select("SELECT * FROM xianyu_first_reply_record WHERE xianyu_account_id = #{accountId} AND xy_goods_id = #{xyGoodsId} AND buyer_user_id = #{buyerUserId} LIMIT 1")
    XianyuFirstReplyRecord selectByBuyerAndGoods(@Param("accountId") Long accountId,
                                                 @Param("xyGoodsId") String xyGoodsId,
                                                 @Param("buyerUserId") String buyerUserId);
}
