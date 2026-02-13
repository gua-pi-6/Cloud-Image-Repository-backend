package com.chen;

import com.chen.config.CosClientConfig;
import com.chen.manager.COSManager;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.StorageClass;
import com.qcloud.cos.model.UploadResult;
import com.qcloud.cos.transfer.TransferManager;
import com.qcloud.cos.transfer.TransferManagerConfiguration;
import com.qcloud.cos.transfer.Upload;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
class SharedPicturePlatformApplicationTests {

    @Resource
    private COSManager cosManager;

    @Test
    void contextLoads() {

        File file = new File("C:/Users/23965/Downloads/作业3.docx");
        String s = cosManager.uploadFileToCOS(file);
        String s1 = cosManager.uploadFileToCOS(file);
        String s2 = cosManager.uploadFileToCOS(file);

        Assertions.assertNotNull(s);
        Assertions.assertNotNull(s1);
        Assertions.assertNotNull(s2);
    }
}



