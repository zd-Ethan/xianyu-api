package com.feijimiao.xianyuassistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsInfoMapper;
import com.feijimiao.xianyuassistant.controller.dto.ItemDTO;
import com.feijimiao.xianyuassistant.service.GoodsInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 商品信息服务实现类
 */
@Slf4j
@Service
public class GoodsInfoServiceImpl implements GoodsInfoService {

    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int STATUS_ON_SALE = 0;
    private static final int STATUS_REVIEWING = 3;
    
    /**
     * 获取当前时间字符串
     */
    private String getCurrentTimeString() {
        return LocalDateTime.now().format(DATETIME_FORMATTER);
    }

    private Integer resolveItemStatus(ItemDTO itemDTO) {
        if (hasReviewingLabel(itemDTO) || isReviewingStatusValue(itemDTO.getItemStatus())) {
            return STATUS_REVIEWING;
        }
        return itemDTO.getItemStatus() != null ? itemDTO.getItemStatus() : STATUS_ON_SALE;
    }

    private boolean isReviewingStatusValue(Integer status) {
        return status != null && status > STATUS_REVIEWING;
    }

    private void applyStatusCondition(LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper, Integer status) {
        if (Integer.valueOf(STATUS_REVIEWING).equals(status)) {
            queryWrapper.and(wrapper -> wrapper.eq(XianyuGoodsInfo::getStatus, STATUS_REVIEWING)
                    .or()
                    .gt(XianyuGoodsInfo::getStatus, STATUS_REVIEWING));
        } else {
            queryWrapper.eq(XianyuGoodsInfo::getStatus, status);
        }
    }

    private void applyStatusesCondition(LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper, List<Integer> statuses) {
        if (statuses.contains(STATUS_REVIEWING)) {
            queryWrapper.and(wrapper -> wrapper.in(XianyuGoodsInfo::getStatus, statuses)
                    .or()
                    .gt(XianyuGoodsInfo::getStatus, STATUS_REVIEWING));
        } else {
            queryWrapper.in(XianyuGoodsInfo::getStatus, statuses);
        }
    }

    private boolean hasReviewingLabel(ItemDTO itemDTO) {
        if (itemDTO == null || itemDTO.getItemLabelDataVO() == null
                || itemDTO.getItemLabelDataVO().getLabelData() == null) {
            return false;
        }

        ItemDTO.LabelData labelData = itemDTO.getItemLabelDataVO().getLabelData();
        return hasReviewingLabel(labelData.getR2())
                || hasReviewingLabel(labelData.getR3())
                || hasReviewingLabel(labelData.getR4());
    }

    private boolean hasReviewingLabel(ItemDTO.TagGroup tagGroup) {
        if (tagGroup == null || tagGroup.getTagList() == null) {
            return false;
        }
        for (ItemDTO.Tag tag : tagGroup.getTagList()) {
            if (tag != null && tag.getData() != null && isReviewingText(tag.getData().getContent())) {
                return true;
            }
        }
        return false;
    }

    private boolean isReviewingText(String text) {
        return text != null && (text.contains("审核")
                || text.contains("待审")
                || text.contains("待发布")
                || text.contains("发布中"));
    }

    private XianyuGoodsInfo selectOneByAccountAndGoods(Long xianyuAccountId, String xyGoodId) {
        LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(XianyuGoodsInfo::getXyGoodId, xyGoodId);
        if (xianyuAccountId != null) {
            queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
        }
        queryWrapper.orderByDesc(XianyuGoodsInfo::getUpdatedTime);
        queryWrapper.last("LIMIT 1");
        return goodsInfoMapper.selectOne(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateGoodsInfo(ItemDTO itemDTO, Long xianyuAccountId) {
        try {
            if (itemDTO == null || itemDTO.getDetailParams() == null) {
                log.warn("商品信息为空，跳过保存");
                return false;
            }
            
            String xyGoodId = itemDTO.getDetailParams().getItemId();
            if (xyGoodId == null || xyGoodId.isEmpty()) {
                log.warn("商品ID为空，跳过保存");
                return false;
            }
            
            // 同一个商品ID可能出现在多个账号下，必须按账号隔离保存
            XianyuGoodsInfo existingGoods = selectOneByAccountAndGoods(xianyuAccountId, xyGoodId);
            
            // 构建商品信息
            XianyuGoodsInfo goodsInfo = new XianyuGoodsInfo();
            goodsInfo.setXyGoodId(xyGoodId);
            goodsInfo.setTitle(itemDTO.getDetailParams().getTitle());
            goodsInfo.setCoverPic(itemDTO.getDetailParams().getPicUrl());
            
            // 将图片信息JSON数组保存到info_pic字段
            String infoPic = itemDTO.getDetailParams().getImageInfos();
            goodsInfo.setInfoPic(infoPic);
            
            // 商品详情页URL
            goodsInfo.setDetailUrl(itemDTO.getDetailUrl());
            
            // 关联闲鱼账号ID
            goodsInfo.setXianyuAccountId(xianyuAccountId);
            
            // 价格信息
            if (itemDTO.getPriceInfo() != null) {
                goodsInfo.setSoldPrice(itemDTO.getPriceInfo().getPrice());
            }
            
            // 商品状态
            goodsInfo.setStatus(resolveItemStatus(itemDTO));
            
            if (existingGoods != null) {
                // 更新现有商品
                goodsInfo.setId(existingGoods.getId());
                goodsInfo.setUpdatedTime(getCurrentTimeString());
                int updated = goodsInfoMapper.updateById(goodsInfo);
                log.info("更新商品信息: xyGoodId={}, title={}, accountId={}", xyGoodId, goodsInfo.getTitle(), xianyuAccountId);
                return updated > 0;
            } else {
                // 新增商品（ID使用雪花算法自动生成）
                goodsInfo.setCreatedTime(getCurrentTimeString());
                goodsInfo.setUpdatedTime(getCurrentTimeString());
                int inserted = goodsInfoMapper.insert(goodsInfo);
                log.info("新增商品信息: xyGoodId={}, title={}, id={}, accountId={}", 
                        xyGoodId, goodsInfo.getTitle(), goodsInfo.getId(), xianyuAccountId);
                return inserted > 0;
            }
            
        } catch (Exception e) {
            log.error("保存或更新商品信息失败: itemId={}", 
                    itemDTO.getDetailParams() != null ? itemDTO.getDetailParams().getItemId() : "null", e);
            throw new RuntimeException("保存或更新商品信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchSaveOrUpdateGoodsInfo(List<ItemDTO> itemList, Long xianyuAccountId) {
        if (itemList == null || itemList.isEmpty()) {
            log.warn("商品列表为空，跳过批量保存");
            return 0;
        }
        
        int successCount = 0;
        for (ItemDTO itemDTO : itemList) {
            try {
                if (saveOrUpdateGoodsInfo(itemDTO, xianyuAccountId)) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("批量保存商品信息时出错: itemId={}", 
                        itemDTO.getDetailParams() != null ? itemDTO.getDetailParams().getItemId() : "null", e);
                // 继续处理下一个商品
            }
        }
        
        log.info("批量保存商品信息完成: 总数={}, 成功={}, accountId={}", itemList.size(), successCount, xianyuAccountId);
        return successCount;
    }

    @Override
    public XianyuGoodsInfo getByXyGoodId(String xyGoodId) {
        try {
            return selectOneByAccountAndGoods(null, xyGoodId);
        } catch (Exception e) {
            log.error("根据闲鱼商品ID查询商品信息失败: xyGoodId={}", xyGoodId, e);
            return null;
        }
    }

    @Override
    public XianyuGoodsInfo getByAccountIdAndXyGoodId(Long xianyuAccountId, String xyGoodId) {
        try {
            return selectOneByAccountAndGoods(xianyuAccountId, xyGoodId);
        } catch (Exception e) {
            log.error("根据账号和闲鱼商品ID查询商品信息失败: accountId={}, xyGoodId={}", xianyuAccountId, xyGoodId, e);
            return null;
        }
    }

    @Override
    public List<XianyuGoodsInfo> listByStatus(Integer status) {
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            applyStatusCondition(queryWrapper, status);
            queryWrapper.orderByDesc(XianyuGoodsInfo::getUpdatedTime);
            return goodsInfoMapper.selectList(queryWrapper);
        } catch (Exception e) {
            log.error("根据状态查询商品列表失败: status={}", status, e);
            return null;
        }
    }
    
    @Override
    public List<XianyuGoodsInfo> listByStatusAndAccountId(Integer status, Long xianyuAccountId) {
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            applyStatusCondition(queryWrapper, status);
            if (xianyuAccountId != null) {
                queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
            }
            queryWrapper.orderByDesc(XianyuGoodsInfo::getUpdatedTime);
            return goodsInfoMapper.selectList(queryWrapper);
        } catch (Exception e) {
            log.error("根据状态和账号ID查询商品列表失败: status={}, accountId={}", status, xianyuAccountId, e);
            return null;
        }
    }
    
    @Override
    public List<XianyuGoodsInfo> listByStatus(Integer status, int pageNum, int pageSize) {
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            applyStatusCondition(queryWrapper, status);
            queryWrapper.orderByDesc(XianyuGoodsInfo::getUpdatedTime);
            
            // 计算偏移量
            int offset = (pageNum - 1) * pageSize;
            
            // 使用MyBatis Plus的分页查询
            return goodsInfoMapper.selectList(queryWrapper.last("LIMIT " + offset + ", " + pageSize));
        } catch (Exception e) {
            log.error("根据状态查询商品列表失败: status={}, pageNum={}, pageSize={}", status, pageNum, pageSize, e);
            return new java.util.ArrayList<>(); // 返回空列表而不是null
        }
    }
    
    @Override
    public List<XianyuGoodsInfo> listByStatusAndAccountId(Integer status, Long xianyuAccountId, int pageNum, int pageSize) {
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            applyStatusCondition(queryWrapper, status);
            if (xianyuAccountId != null) {
                queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
            }
            queryWrapper.orderByDesc(XianyuGoodsInfo::getUpdatedTime);
            
            // 计算偏移量
            int offset = (pageNum - 1) * pageSize;
            
            // 使用MyBatis Plus的分页查询
            return goodsInfoMapper.selectList(queryWrapper.last("LIMIT " + offset + ", " + pageSize));
        } catch (Exception e) {
            log.error("根据状态和账号ID查询商品列表失败: status={}, accountId={}, pageNum={}, pageSize={}", 
                    status, xianyuAccountId, pageNum, pageSize, e);
            return new java.util.ArrayList<>(); // 返回空列表而不是null
        }
    }

    @Override
    public List<XianyuGoodsInfo> listByStatusesAndAccountId(List<Integer> statuses, Long xianyuAccountId, int pageNum, int pageSize) {
        try {
            if (statuses == null || statuses.isEmpty()) {
                return new java.util.ArrayList<>();
            }

            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            applyStatusesCondition(queryWrapper, statuses);
            if (xianyuAccountId != null) {
                queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
            }
            queryWrapper.orderByDesc(XianyuGoodsInfo::getUpdatedTime);

            int offset = (pageNum - 1) * pageSize;
            return goodsInfoMapper.selectList(queryWrapper.last("LIMIT " + offset + ", " + pageSize));
        } catch (Exception e) {
            log.error("根据多个状态和账号ID查询商品列表失败: statuses={}, accountId={}, pageNum={}, pageSize={}",
                    statuses, xianyuAccountId, pageNum, pageSize, e);
            return new java.util.ArrayList<>();
        }
    }
    
    @Override
    public List<XianyuGoodsInfo> listByAccountId(Long xianyuAccountId, int pageNum, int pageSize) {
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            if (xianyuAccountId != null) {
                queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
            }
            queryWrapper.orderByDesc(XianyuGoodsInfo::getUpdatedTime);
            int offset = (pageNum - 1) * pageSize;
            return goodsInfoMapper.selectList(queryWrapper.last("LIMIT " + offset + ", " + pageSize));
        } catch (Exception e) {
            log.error("根据账号ID查询商品列表失败: accountId={}, pageNum={}, pageSize={}", xianyuAccountId, pageNum, pageSize, e);
            return new java.util.ArrayList<>();
        }
    }
    
    @Override
    public int countByStatusAndAccountId(Integer status, Long xianyuAccountId) {
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            applyStatusCondition(queryWrapper, status);
            if (xianyuAccountId != null) {
                queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
            }
            return Math.toIntExact(goodsInfoMapper.selectCount(queryWrapper));
        } catch (Exception e) {
            log.error("根据状态和账号ID统计商品数量失败: status={}, accountId={}", status, xianyuAccountId, e);
            return 0;
        }
    }

    @Override
    public int countByStatusesAndAccountId(List<Integer> statuses, Long xianyuAccountId) {
        try {
            if (statuses == null || statuses.isEmpty()) {
                return 0;
            }

            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            applyStatusesCondition(queryWrapper, statuses);
            if (xianyuAccountId != null) {
                queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
            }
            return Math.toIntExact(goodsInfoMapper.selectCount(queryWrapper));
        } catch (Exception e) {
            log.error("根据多个状态和账号ID统计商品数量失败: statuses={}, accountId={}", statuses, xianyuAccountId, e);
            return 0;
        }
    }
    
    @Override
    public int countByAccountId(Long xianyuAccountId) {
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            if (xianyuAccountId != null) {
                queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
            }
            return Math.toIntExact(goodsInfoMapper.selectCount(queryWrapper));
        } catch (Exception e) {
            log.error("根据账号ID统计商品数量失败: accountId={}", xianyuAccountId, e);
            return 0;
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDetailInfo(String xyGoodId, String detailInfo) {
        return updateDetailInfo(null, xyGoodId, detailInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDetailInfo(Long xianyuAccountId, String xyGoodId, String detailInfo) {
        try {
            XianyuGoodsInfo existingGoods = selectOneByAccountAndGoods(xianyuAccountId, xyGoodId);
            
            if (existingGoods == null) {
                log.warn("商品不存在，无法更新详情: accountId={}, xyGoodId={}", xianyuAccountId, xyGoodId);
                return false;
            }
            
            existingGoods.setDetailInfo(detailInfo);
            existingGoods.setUpdatedTime(getCurrentTimeString());
            int updated = goodsInfoMapper.updateById(existingGoods);
            
            log.info("更新商品详情成功: accountId={}, xyGoodId={}, 详情长度={}", 
                    xianyuAccountId, xyGoodId, detailInfo != null ? detailInfo.length() : 0);
            return updated > 0;
        } catch (Exception e) {
            log.error("更新商品详情失败: accountId={}, xyGoodId={}", xianyuAccountId, xyGoodId, e);
            throw new RuntimeException("更新商品详情失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getDetailInfoByGoodsId(String xyGoodId) {
        XianyuGoodsInfo goods = selectOneByAccountAndGoods(null, xyGoodId);
        return goods != null ? goods.getDetailInfo() : null;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteGoodsInfo(Long xianyuAccountId, String xyGoodId) {
        try {
            // 查询商品信息
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(XianyuGoodsInfo::getXyGoodId, xyGoodId);
            queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
            XianyuGoodsInfo existingGoods = goodsInfoMapper.selectOne(queryWrapper);
            
            if (existingGoods == null) {
                log.warn("商品不存在，无法删除: xyGoodId={}, accountId={}", xyGoodId, xianyuAccountId);
                return false;
            }
            
            // 删除商品
            int deleted = goodsInfoMapper.deleteById(existingGoods.getId());
            
            log.info("删除商品成功: xyGoodId={}, title={}, id={}, accountId={}", 
                    xyGoodId, existingGoods.getTitle(), existingGoods.getId(), xianyuAccountId);
            return deleted > 0;
        } catch (Exception e) {
            log.error("删除商品失败: xyGoodId={}, accountId={}", xyGoodId, xianyuAccountId, e);
            throw new RuntimeException("删除商品失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSkuCount(String xyGoodId, int skuCount) {
        return updateSkuCount(null, xyGoodId, skuCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSkuCount(Long xianyuAccountId, String xyGoodId, int skuCount) {
        try {
            XianyuGoodsInfo existingGoods = selectOneByAccountAndGoods(xianyuAccountId, xyGoodId);

            if (existingGoods == null) {
                log.warn("商品不存在，无法更新SKU数量: accountId={}, xyGoodId={}", xianyuAccountId, xyGoodId);
                return false;
            }

            existingGoods.setSkuCount(skuCount);
            existingGoods.setUpdatedTime(getCurrentTimeString());
            int updated = goodsInfoMapper.updateById(existingGoods);

            log.info("更新商品SKU数量成功: accountId={}, xyGoodId={}, skuCount={}", xianyuAccountId, xyGoodId, skuCount);
            return updated > 0;
        } catch (Exception e) {
            log.error("更新商品SKU数量失败: accountId={}, xyGoodId={}", xianyuAccountId, xyGoodId, e);
            return false;
        }
    }

    @Override
    public void markOfflineIfNotInRemote(Long xianyuAccountId, Set<String> remoteItemIds) {
        if (xianyuAccountId == null || remoteItemIds == null) return;
        try {
            LambdaQueryWrapper<XianyuGoodsInfo> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(XianyuGoodsInfo::getXianyuAccountId, xianyuAccountId);
            queryWrapper.in(XianyuGoodsInfo::getStatus, 0, 1);
            List<XianyuGoodsInfo> localItems = goodsInfoMapper.selectList(queryWrapper);

            int markedCount = 0;
            for (XianyuGoodsInfo localItem : localItems) {
                if (!remoteItemIds.contains(localItem.getXyGoodId())) {
                    localItem.setStatus(-1);
                    localItem.setUpdatedTime(getCurrentTimeString());
                    goodsInfoMapper.updateById(localItem);
                    markedCount++;
                    log.info("商品已删除(远程不存在): xyGoodId={}, title={}", localItem.getXyGoodId(), localItem.getTitle());
                }
            }
            if (markedCount > 0) {
                log.info("【账号{}】同步时标记{}个商品为已删除(status=-1)", xianyuAccountId, markedCount);
            }
        } catch (Exception e) {
            log.error("标记删除商品失败: accountId={}", xianyuAccountId, e);
        }
    }
}
