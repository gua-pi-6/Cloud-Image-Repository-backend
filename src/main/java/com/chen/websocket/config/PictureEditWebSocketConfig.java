package com.chen.websocket.config;

import com.chen.websocket.handler.PictureEditWebSocketHandler;
import com.chen.websocket.interceptor.PictureEditHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

@Configuration
@EnableWebSocket
/**
 * 图片协同编辑 WebSocket 注册入口。
 *
 * <p>这里只做两件事：
 * 1. 把处理器挂到固定路径；
 * 2. 把握手阶段的鉴权拦截器接入进来。
 */
public class PictureEditWebSocketConfig implements WebSocketConfigurer {

    @Resource
    private PictureEditWebSocketHandler pictureEditWebSocketHandler;

    @Resource
    private PictureEditHandshakeInterceptor pictureEditHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 协同编辑入口统一收敛到一个 handler，后续按 pictureId 分房间。
        registry.addHandler(pictureEditWebSocketHandler, "/ws/picture/edit")
                .addInterceptors(pictureEditHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
