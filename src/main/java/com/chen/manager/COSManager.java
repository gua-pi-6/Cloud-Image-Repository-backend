package com.chen.manager;

import cn.hutool.core.io.FileUtil;
import com.chen.config.CosClientConfig;
import com.chen.constant.FileConstant;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.ciModel.common.ImageProcessRequest;
import com.qcloud.cos.model.ciModel.persistence.CIUploadResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.qcloud.cos.transfer.Download;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.TransferManagerConfiguration;
import com.qcloud.cos.transfer.Upload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@Component
public class COSManager {

    @Resource
    private COSClient cosClient;

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private TransferManager transferManager;


    /**
     * 删除对象
     *
     * @param key 文件 key
     */
    public void deleteObject(String key){
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }


    /**
     * 上传并处理 COS 图片 (转换为 WebP)
     *
     * @param key  原始文件的路径（例如: public/id/日期_UUID.webp）
     * @param file 本地文件
     * @return PutObjectResult
     */
    public PutObjectResult uploadAndHandleCosPicture(String key, File file) {
        String bucketName = cosClientConfig.getBucket();

        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, file);


        // 创建图片处理配置对象
        PicOperations picOperations = new PicOperations();
        picOperations.setIsPicInfo(1);
        // 创建处理规则列表
        List<PicOperations.Rule> rules = new ArrayList<>();
        PicOperations.Rule rule = new PicOperations.Rule();
        // 将图片转换为 WebP 格式
        String webpKey = FileUtil.mainName(key) + ".webp";
        rule.setFileId(webpKey); // 设置处理后的文件路径
        // 设置 bucket
        rule.setBucket(bucketName);
        //【核心】设置处理规则：imageMogr2 是基础处理，/format/webp 表示转为 webp
        // 你还可以加上 /quality/75 来指定压缩质量
        rule.setRule("imageMogr2/format/webp");
        rules.add(rule);
        // 只有当图片大小超过 20KB 时才处理成缩略图
        if (file.length() > 20 * 1024){
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail" + ".webp";
            thumbnailRule.setFileId(thumbnailKey);
            thumbnailRule.setBucket(bucketName);
            // 解释：
            // thumbnail/!640x480r : 强制缩放并填充到 640x480 的矩形中（可能会有部分被裁掉，但填满）
            // gravity/Center      : 从图片中心开始裁剪，保留中间主要内容
            // format/webp         : 转为 WebP
            // quality/80          : 质量 80
            thumbnailRule.setRule("imageMogr2/thumbnail/!640x480r/gravity/Center/crop/640x480/format/webp/quality/80");
            rules.add(thumbnailRule);
        }

        picOperations.setRules(rules);

        // 设置到请求中
        putObjectRequest.setPicOperations(picOperations);

        PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
        // 因为你需要的是 .webp 文件，原有的 .png/.jpg 文件已经没用了
        try {
            this.deleteObject(key);
        } catch (Exception e) {
            // 建议打印日志，防止删除失败没人知道
            log.error("删除原图失败: {}", key, e);
        }

        return putObjectResult;
    }


    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }


    // 上传文件到 COS 对象存储中
    public String uploadFileToCOS(File localFile) {
        // 设置高级接口的配置项
        // 分块上传阈值和分块大小分别设置为 5MB 和 1MB（若不特殊设置，分块上传阈值和分块大小的默认值均为5MB）
        TransferManagerConfiguration transferManagerConfiguration = new TransferManagerConfiguration();
        transferManagerConfiguration.setMultipartUploadThreshold(5 * 1024 * 1024);
        transferManagerConfiguration.setMinimumUploadPartSize(1024 * 1024);
        transferManager.setConfiguration(transferManagerConfiguration);

        // 存储桶的命名格式为 BucketName-APPID，此处填写的存储桶名称必须为此格式
        String bucketName = cosClientConfig.getBucket();

        // 对象键(Key)是对象在存储桶中的唯一标识。
        String key = generateKey(localFile.getName());

        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, key, localFile);

        try {
            // 高级接口会返回一个异步结果Upload
            // 可同步地调用 waitForUploadResult 方法等待上传完成，成功返回 UploadResult, 失败抛出异常
            Upload upload = transferManager.upload(putObjectRequest);
            UploadResult uploadResult = upload.waitForUploadResult();
        } catch (CosServiceException e) {
            e.printStackTrace();
        } catch (CosClientException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return cosClientConfig.getHost() + key;
    }


    // 应用关闭后自动关闭 TransferManager
    @PreDestroy
    private void cleanup() {
        if (transferManager != null) {
            shutdownTransferManager(transferManager);
        }
    }

    // 关闭 TransferManager
    private void shutdownTransferManager(TransferManager transferManager) {
        // 指定参数为 true, 则同时会关闭 transferManager 内部的 COSClient 实例。
        // 指定参数为 false, 则不会关闭 transferManager 内部的 COSClient 实例。
        transferManager.shutdownNow(true);
    }

    // 生成key
    private String generateKey(String fileName) {
        String filePrefix = FileConstant.FILE_PREFIX;
        return filePrefix + "/" + fileName;
    }
}
