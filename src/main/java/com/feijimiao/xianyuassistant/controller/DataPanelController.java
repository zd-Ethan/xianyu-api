package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.cache.CacheService;
import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.DataPanelStatsRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.DataPanelTrendRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.SalesRevenueRespDTO;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoReplyRecordMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/data-panel")
@CrossOrigin(origins = "*")
public class DataPanelController {

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired
    private XianyuGoodsAutoReplyRecordMapper replyRecordMapper;

    @Autowired
    private CacheService cacheService;

    private static final long CACHE_TTL_MINUTES = 30;

    @PostMapping("/stats")
    public ResultObject<DataPanelStatsRespDTO> getDataPanelStats(@RequestBody(required = false) StatsReq req) {
        try {
            String date;
            if (req != null && req.getDate() != null && !req.getDate().isEmpty()) {
                date = req.getDate();
            } else {
                date = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            }

            boolean isToday = date.equals(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
            String cacheKey = "dataPanelStats:" + date;

            if (!isToday) {
                DataPanelStatsRespDTO cached = cacheService.get(cacheKey, DataPanelStatsRespDTO.class);
                if (cached != null) {
                    return ResultObject.success(cached);
                }
            }

            DataPanelStatsRespDTO respDTO = new DataPanelStatsRespDTO();
            Map<String, Object> orderStats = orderMapper.selectDataPanelStatsByDate(date);
            respDTO.setOrderCount(toInt(orderStats, "orderCount"));
            respDTO.setDeliverySuccessCount(toInt(orderStats, "deliverySuccessCount"));
            respDTO.setDeliveryFailCount(toInt(orderStats, "deliveryFailCount"));
            respDTO.setAiReplyCount(replyRecordMapper.countAiRepliesByDate(date));
            respDTO.setHasData(orderMapper.countAllOrders() > 0 || replyRecordMapper.countAllReplies() > 0);

            if (!isToday) {
                cacheService.put(cacheKey, respDTO, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            }

            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取数据面板统计失败", e);
            return ResultObject.failed("获取数据面板统计失败: " + e.getMessage());
        }
    }

    @PostMapping("/trend")
    public ResultObject<DataPanelTrendRespDTO> getDataPanelTrend() {
        try {
            String cacheKey = "dataPanelTrend:" + LocalDate.now();
            DataPanelTrendRespDTO cached = cacheService.get(cacheKey, DataPanelTrendRespDTO.class);
            if (cached != null) {
                return ResultObject.success(cached);
            }

            DataPanelTrendRespDTO respDTO = new DataPanelTrendRespDTO();
            List<String> dates = new ArrayList<>();
            List<Integer> deliverySuccess = new ArrayList<>();
            List<Integer> deliveryFail = new ArrayList<>();
            List<Integer> aiReplies = new ArrayList<>();

            LocalDate today = LocalDate.now();
            LocalDate startDate = today.minusDays(7);
            LocalDate endDate = today.minusDays(1);
            DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;

            Map<String, Map<String, Object>> orderTrendMap = toDateRowMap(
                    orderMapper.selectDeliveryTrendByDateRange(startDate.format(fmt), endDate.format(fmt))
            );
            Map<String, Map<String, Object>> replyTrendMap = toDateRowMap(
                    replyRecordMapper.selectAiReplyTrendByDateRange(startDate.format(fmt), endDate.format(fmt))
            );

            for (int i = 7; i >= 1; i--) {
                LocalDate d = today.minusDays(i);
                String dateStr = d.format(fmt);
                Map<String, Object> orderRow = orderTrendMap.get(dateStr);
                Map<String, Object> replyRow = replyTrendMap.get(dateStr);

                dates.add(d.getMonthValue() + "/" + d.getDayOfMonth());
                deliverySuccess.add(toInt(orderRow, "deliverySuccessCount"));
                deliveryFail.add(toInt(orderRow, "deliveryFailCount"));
                aiReplies.add(toInt(replyRow, "aiReplyCount"));
            }

            respDTO.setDates(dates);
            respDTO.setDeliverySuccess(deliverySuccess);
            respDTO.setDeliveryFail(deliveryFail);
            respDTO.setAiReplies(aiReplies);

            cacheService.put(cacheKey, respDTO, 10, TimeUnit.MINUTES);

            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取数据面板趋势失败", e);
            return ResultObject.failed("获取数据面板趋势失败: " + e.getMessage());
        }
    }

    @GetMapping("/realtimeRevenue")
    public ResultObject<Double> getRealtimeRevenue() {
        try {
            double amount = orderMapper.sumDeliverySuccessAmount();
            return ResultObject.success(amount);
        } catch (Exception e) {
            log.error("获取实时销售额失败", e);
            return ResultObject.failed("获取实时销售额失败: " + e.getMessage());
        }
    }

    @lombok.Data
    public static class StatsReq {
        private String date;
    }

    @PostMapping("/salesRevenue")
    public ResultObject<SalesRevenueRespDTO> getSalesRevenue(@RequestBody SalesRevenueReq req) {
        try {
            String dimension = req != null && req.getDimension() != null ? req.getDimension() : "day";
            String startDateStr = req != null ? req.getStartDate() : null;
            String endDateStr = req != null ? req.getEndDate() : null;

            LocalDate endDate;
            LocalDate startDate;
            DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;

            if (endDateStr != null && !endDateStr.isEmpty()) {
                endDate = LocalDate.parse(endDateStr, fmt);
            } else {
                endDate = LocalDate.now();
            }

            if (startDateStr != null && !startDateStr.isEmpty()) {
                startDate = LocalDate.parse(startDateStr, fmt);
            } else {
                startDate = endDate.minusDays(9);
            }

            boolean isToday = !endDate.isBefore(LocalDate.now());
            String cacheKey = "salesRevenue:" + dimension + ":" + startDate + ":" + endDate;

            if (!isToday) {
                SalesRevenueRespDTO cached = cacheService.get(cacheKey, SalesRevenueRespDTO.class);
                if (cached != null) {
                    log.debug("销售额趋势命中缓存: {}", cacheKey);
                    return ResultObject.success(cached);
                }
            }

            SalesRevenueRespDTO respDTO = new SalesRevenueRespDTO();
            List<String> labels = new ArrayList<>();
            List<Double> values = new ArrayList<>();

            switch (dimension) {
                case "week":
                    buildWeeklyData(startDate, endDate, labels, values);
                    break;
                case "month":
                    buildMonthlyData(startDate, endDate, labels, values);
                    break;
                case "quarter":
                    buildQuarterlyData(startDate, endDate, labels, values);
                    break;
                default:
                    buildDailyData(startDate, endDate, labels, values);
                    break;
            }

            respDTO.setLabels(labels);
            respDTO.setValues(values);

            if (!isToday) {
                cacheService.put(cacheKey, respDTO, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            }

            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取销售额趋势失败", e);
            return ResultObject.failed("获取销售额趋势失败: " + e.getMessage());
        }
    }

    private void buildDailyData(LocalDate startDate, LocalDate endDate, List<String> labels, List<Double> values) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("M/d");
        Map<String, Map<String, Object>> amountMap = toDateRowMap(
                orderMapper.sumDailyDeliverySuccessAmountByDateRange(startDate.format(fmt), endDate.format(fmt))
        );

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            String dateStr = d.format(fmt);
            labels.add(d.format(labelFmt));
            values.add(toDouble(amountMap.get(dateStr), "amount"));
        }
    }

    private void buildWeeklyData(LocalDate startDate, LocalDate endDate, List<String> labels, List<Double> values) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        LocalDate weekStart = startDate;
        int weekNum = 1;
        while (!weekStart.isAfter(endDate)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            if (weekEnd.isAfter(endDate)) weekEnd = endDate;
            labels.add("第" + weekNum + "周");
            values.add(orderMapper.sumDeliverySuccessAmountByDateRange(weekStart.format(fmt), weekEnd.format(fmt)));
            weekStart = weekEnd.plusDays(1);
            weekNum++;
        }
    }

    private void buildMonthlyData(LocalDate startDate, LocalDate endDate, List<String> labels, List<Double> values) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        LocalDate monthStart = startDate.withDayOfMonth(1);
        while (!monthStart.isAfter(endDate)) {
            LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
            if (monthEnd.isAfter(endDate)) monthEnd = endDate;
            if (monthStart.isBefore(startDate)) monthStart = startDate;
            labels.add(monthStart.getYear() + "/" + monthStart.getMonthValue());
            values.add(orderMapper.sumDeliverySuccessAmountByDateRange(monthStart.format(fmt), monthEnd.format(fmt)));
            monthStart = monthStart.plusMonths(1).withDayOfMonth(1);
        }
    }

    private void buildQuarterlyData(LocalDate startDate, LocalDate endDate, List<String> labels, List<Double> values) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        int startQuarter = (startDate.getMonthValue() - 1) / 3 + 1;
        LocalDate quarterStart = startDate.withMonth((startQuarter - 1) * 3 + 1).withDayOfMonth(1);
        while (!quarterStart.isAfter(endDate)) {
            int quarter = (quarterStart.getMonthValue() - 1) / 3 + 1;
            LocalDate quarterEnd = quarterStart.plusMonths(3).minusDays(1);
            if (quarterEnd.isAfter(endDate)) quarterEnd = endDate;
            LocalDate actualStart = quarterStart.isBefore(startDate) ? startDate : quarterStart;
            labels.add(quarterStart.getYear() + "Q" + quarter);
            values.add(orderMapper.sumDeliverySuccessAmountByDateRange(actualStart.format(fmt), quarterEnd.format(fmt)));
            quarterStart = quarterStart.plusMonths(3);
        }
    }

    @lombok.Data
    public static class SalesRevenueReq {
        private String dimension;
        private String startDate;
        private String endDate;
    }

    private Map<String, Map<String, Object>> toDateRowMap(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        if (rows == null) {
            return result;
        }

        for (Map<String, Object> row : rows) {
            String date = toStringValue(row, "date");
            if (date != null && !date.isEmpty()) {
                result.put(date, row);
            }
        }
        return result;
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

    private double toDouble(Map<String, Object> row, String key) {
        Object value = getValue(row, key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0D;
        }
    }

    private String toStringValue(Map<String, Object> row, String key) {
        Object value = getValue(row, key);
        return value == null ? null : value.toString();
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
