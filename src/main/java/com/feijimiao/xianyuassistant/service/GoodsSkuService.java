package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsSku;

import java.util.List;

public interface GoodsSkuService {

    List<XianyuGoodsSku> listByXyGoodsId(String xyGoodsId);

    List<XianyuGoodsSku> listByAccountIdAndXyGoodsId(Long xianyuAccountId, String xyGoodsId);

    int countByXyGoodsId(String xyGoodsId);

    void saveSkus(String xyGoodsId, Long xianyuAccountId, List<XianyuGoodsSku> skuList);

    void deleteByXyGoodsId(String xyGoodsId);

    void deleteByAccountIdAndXyGoodsId(Long xianyuAccountId, String xyGoodsId);
}
