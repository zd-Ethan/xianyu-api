package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuSalesSyncState;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface XianyuSalesSyncStateMapper extends BaseMapper<XianyuSalesSyncState> {

    @Delete("DELETE FROM xianyu_sales_sync_state WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);

    @Insert("""
            INSERT INTO xianyu_sales_sync_state (
                xianyu_account_id, sync_status, last_started_at, last_error
            ) VALUES (#{accountId}, 'syncing', #{startedAt}, NULL)
            ON CONFLICT(xianyu_account_id) DO UPDATE SET
                sync_status = 'syncing',
                last_started_at = excluded.last_started_at,
                last_error = NULL,
                data_quality_error_count = 0
            """)
    int markSyncing(@Param("accountId") Long accountId, @Param("startedAt") String startedAt);

    @Update("""
            UPDATE xianyu_sales_sync_state
            SET sync_status = 'success', last_success_at = #{successAt},
                last_incremental_success_at = CASE WHEN #{incremental}
                    THEN #{successAt} ELSE last_incremental_success_at END,
                last_full_success_at = CASE WHEN #{incremental}
                    THEN last_full_success_at ELSE #{successAt} END,
                last_error = NULL, synced_order_count = #{orderCount},
                data_quality_error_count = #{dataQualityErrorCount}
            WHERE xianyu_account_id = #{accountId}
            """)
    int markSuccess(
            @Param("accountId") Long accountId,
            @Param("successAt") String successAt,
            @Param("orderCount") int orderCount,
            @Param("dataQualityErrorCount") int dataQualityErrorCount,
            @Param("incremental") boolean incremental
    );

    @Insert("""
            INSERT INTO xianyu_sales_sync_state (
                xianyu_account_id, sync_status, last_started_at, last_error,
                data_quality_error_count
            ) VALUES (#{accountId}, 'failed', #{failedAt}, #{errorMessage}, #{dataQualityErrorCount})
            ON CONFLICT(xianyu_account_id) DO UPDATE SET
                sync_status = 'failed',
                last_error = excluded.last_error,
                data_quality_error_count = excluded.data_quality_error_count
            """)
    int markFailed(
            @Param("accountId") Long accountId,
            @Param("failedAt") String failedAt,
            @Param("errorMessage") String errorMessage,
            @Param("dataQualityErrorCount") int dataQualityErrorCount
    );

    @Select("SELECT * FROM xianyu_sales_sync_state WHERE xianyu_account_id = #{accountId}")
    XianyuSalesSyncState selectByAccountId(@Param("accountId") Long accountId);

    @Select("""
            SELECT state.*
            FROM xianyu_sales_sync_state state
            INNER JOIN xianyu_account account ON account.id = state.xianyu_account_id
            ORDER BY state.xianyu_account_id
            """)
    List<XianyuSalesSyncState> selectAll();
}
