package com.chen.manager.fileupload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.chen.exception.BusinessException;
import com.chen.exception.ErrorCode;
import com.chen.exception.ThrowUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import java.io.File;
import java.util.Arrays;
import java.util.List;

@Component
public class UrlPictureUpload extends PictureUploadTemplate {
    @Override
    protected void saveFileContent(Object inputSource, File targetPath) {
        HttpUtil.downloadFile((String) inputSource, targetPath);
    }

    @Override
    protected String getInputSourceFilename(Object inputSource) {
        return FileUtil.getName((String) inputSource);
    }

    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 0. 基础非空校验
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址不能为空");

        // 1. 校验文件后缀 (建议提前校验，避免不必要的网络请求)
        // 需要处理 URL 可能带参数的情况 (如: image.png?token=123)
        // 使用 Hutool 的 URLUtil 获取路径部分，再取后缀
        String urlPath = URLUtil.url(fileUrl).getPath();
        String fileSuffix = FileUtil.getSuffix(urlPath);

        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        // 统一转小写并在列表中检查
        ThrowUtils.throwIf(StrUtil.isBlank(fileSuffix) || !ALLOW_FORMAT_LIST.contains(fileSuffix.toLowerCase()),
                ErrorCode.PARAMS_ERROR, "文件类型错误");

        // 2. 校验文件大小
        // 优化点：不要直接下载文件！使用 HEAD 请求读取 Content-Length 头部信息即可
        long fileSize = 0;
        try {
            // 发送 HEAD 请求
            HttpResponse response = HttpRequest.head(fileUrl).execute();
            // 再次确认状态码
            ThrowUtils.throwIf(!response.isOk(), ErrorCode.PARAMS_ERROR, "获取网络图片失败");

            // 获取文件大小
            fileSize = response.contentLength();

            // 如果服务器没返回 Content-Length (fileSize == -1)，不得不下载少量数据或全部数据来判断
            // 这里为了兜底，如果拿不到长度，则尝试下载字节数组（因为限制是2M，内存能承受）
            if (fileSize == -1) {
                byte[] bytes = HttpUtil.downloadBytes(fileUrl);
                fileSize = bytes.length;
            }

            // 关闭连接
            response.close();
        } catch (Exception e) {
            // 捕获网络异常
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "网络图片校验失败: " + e.getMessage());
        }

        final long ONE_M = 1024 * 1024L;
        ThrowUtils.throwIf(fileSize > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");
    }
}
