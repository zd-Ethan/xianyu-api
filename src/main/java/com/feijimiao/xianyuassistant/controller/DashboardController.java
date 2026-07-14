package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsInfoMapper;
import com.feijimiao.xianyuassistant.controller.dto.DashboardStatsRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 首页仪表板控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private XianyuAccountMapper accountMapper;
    
    @Autowired
    private XianyuGoodsInfoMapper goodsMapper;

    /**
     * 获取首页统计数据
     */
    @PostMapping("/stats")
    public ResultObject<DashboardStatsRespDTO> getDashboardStats() {
        try {
            log.info("获取首页统计数据");
            
            // 获取账号总数
            int accountCount = accountMapper.selectCount(null).intValue();
            
            // 聚合获取商品统计，避免多次 count 查询
            Map<String, Object> goodsStats = goodsMapper.selectDashboardGoodsStats();
            
            // 构造响应数据
            DashboardStatsRespDTO respDTO = new DashboardStatsRespDTO();
            respDTO.setAccountCount(accountCount);
            respDTO.setItemCount(toInt(goodsStats, "itemCount"));
            respDTO.setSellingItemCount(toInt(goodsStats, "sellingItemCount"));
            respDTO.setOffShelfItemCount(toInt(goodsStats, "offShelfItemCount"));
            respDTO.setSoldItemCount(toInt(goodsStats, "soldItemCount"));
            
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取首页统计数据失败", e);
            return ResultObject.failed("获取首页统计数据失败: " + e.getMessage());
        }
    }

    private int toInt(Map<String, Object> row, String key) {
        Object value = getValue(row, key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Object getValue(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String underscoreKey = camelToSnake(key);
        if (row.containsKey(underscoreKey)) {
            return row.get(underscoreKey);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(underscoreKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String camelToSnake(String key) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c)) {
                builder.append('_').append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
