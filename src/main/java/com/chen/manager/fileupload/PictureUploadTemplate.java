package com.chen.manager.fileupload;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.chen.config.CosClientConfig;
import com.chen.exception.BusinessException;
import com.chen.exception.ErrorCode;
import com.chen.manager.COSManager;
import com.chen.model.dto.file.UploadPictureResult;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;


import javax.annotation.Resource;
import java.io.File;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSManager cosManager;

    /**
     * 上传图片
     *
     * @param inputSource    输入源
     * @param uploadPathPrefix 上传路径前缀
     * @return 上传结果
     */
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix){
        // 校验图片
        validPicture(inputSource);
        // 图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originFilename = this.getInputSourceFilename(inputSource);
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originFilename));
        String uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFilename);
        File file = null;
        try {
            // 创建临时文件
            file = File.createTempFile(uploadPath, null);
            this.saveFileContent(inputSource, file);
            // 上传图片
            PutObjectResult putObjectResult = cosManager.uploadAndHandleCosPicture(uploadPath, file);
            List<CIObject> ciObjectList = putObjectResult.getCiUploadResult().getProcessResults().getObjectList();

            if (!ciObjectList.isEmpty()){
                // 获取处理成webp的图片信息
                CIObject imageInfo = ciObjectList.get(0);
                // 获取处理成webp的缩略图信息
                CIObject thumbnail = imageInfo;
                if (ciObjectList.size() > 1){
                    thumbnail = ciObjectList.get(1);
                }

                return buildResult(originFilename, imageInfo, thumbnail);
            }else {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
            }

        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            this.deleteTempFile(file);
        }
    }

    /**
     * 保存文件内容到指定路径
     * @param inputSource 输入源
     * @param targetPath 目标路径
     */
    protected abstract void saveFileContent(Object inputSource, File targetPath);

    /**
     * 获取输入源文件名
     * @param inputSource 输入源
     */
    protected abstract String getInputSourceFilename(Object inputSource);

    private UploadPictureResult buildResult(String originFilename, CIObject compressedCiObject, CIObject thumbnail) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnail.getKey());
        // 设置图片为压缩后的地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedCiObject.getKey());
        return uploadPictureResult;
    }


    /**
     * 校验图片
     */
    protected abstract void validPicture(Object inputSource);



    /**
     * 删除临时文件
     * @param file 临时文件
     */
    private void deleteTempFile(File file) {
        if (file == null) {
            return;
        }

        // 删除临时文件
        boolean deleteResult = file.delete();
        if (!deleteResult) {
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }
    }

}
