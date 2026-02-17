package com.chen.api;


import com.chen.model.result.imagesearch.ImageSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ImageSearchApiFacade {

    @Resource
    private PexelsImageSearch pexelsImageSearch;

    /**
     * 搜索图片
     *
     * @param query 图片关键字（中文）
     * @return 图片搜索结果列表
     */
    public List<ImageSearchResult> searchImage(String query) {
        // 先进行中文转英文
        List<String> imageUrls = pexelsImageSearch.searchPicturesForChinese(query,12);

        // 将图片的URL转为ImageSearchResult对象
        return this.convertToImageSearchResults(imageUrls);
    }

    /**
     * 将图片URL列表转换为ImageSearchResult列表
     *
     * @param imageUrls 图片URL列表
     * @return ImageSearchResult列表
     */
    private List<ImageSearchResult> convertToImageSearchResults(List<String> imageUrls) {
        List<ImageSearchResult> imageSearchResults = new ArrayList<>();
        for (String url : imageUrls) {
            ImageSearchResult result = new ImageSearchResult();
            // 使用Pexels提供的原图URL作为缩略图
            result.setThumbUrl(url);
            // 这里假设来源地址与缩略图相同
            result.setFromUrl(url);
            imageSearchResults.add(result);
        }
        return imageSearchResults;
    }

}
