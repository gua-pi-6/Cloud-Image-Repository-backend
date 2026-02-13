package com.chen.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.transfer.TransferManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class COSTransferManagerConfig {

    @Resource
    private COSClient cosClient;

    @Bean
    // 创建 TransferManager 实例，这个实例用来后续调用高级接口
    public TransferManager createTransferManager() {

        // 自定义线程池大小，建议在客户端与 COS 网络充足（例如使用腾讯云的 CVM，同地域上传 COS）的情况下，设置成16或32即可，可较充分的利用网络资源
        // 对于使用公网传输且网络带宽质量不高的情况，建议减小该值，避免因网速过慢，造成请求超时。
        ExecutorService threadPool = Executors.newFixedThreadPool(32);

        // 传入一个 threadpool, 若不传入线程池，默认 TransferManager 中会生成一个单线程的线程池。
        TransferManager transferManager = new TransferManager(cosClient, threadPool);

        return transferManager;
    }
}
