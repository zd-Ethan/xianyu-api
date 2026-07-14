package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsSkuProperty;

import java.util.List;

public interface GoodsSkuPropertyService {

    List<XianyuGoodsSkuProperty> listByXyGoodsId(String xyGoodsId);

    List<XianyuGoodsSkuProperty> listByAccountIdAndXyGoodsId(Long xianyuAccountId, String xyGoodsId);

    void saveProperties(String xyGoodsId, Long xianyuAccountId, List<XianyuGoodsSkuProperty> propertyList);

    void deleteByXyGoodsId(String xyGoodsId);

    void deleteByAccountIdAndXyGoodsId(Long xianyuAccountId, String xyGoodsId);
}
