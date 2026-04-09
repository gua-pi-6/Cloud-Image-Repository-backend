package com.chen.websocket.model;

import lombok.Data;

@Data
/**
 * 客户端发给服务端的协同消息。
 *
 * <p>这个对象同时承载三类消息：
 * JOIN / PING / OP。
 * 其中真正参与协同算法的是 OP 相关字段。
 */
public class PictureEditClientMessage {

    /**
     * 消息大类，例如 JOIN / PING / OP。
     */
    private String type;

    /**
     * 客户端操作唯一 id，用于幂等去重。
     */
    private String opId;

    /**
     * 客户端认为自己正在编辑的图片 id。
     */
    private Long pictureId;

    /**
     * 客户端发起本次操作时所基于的服务端版本号。
     */
    private Long baseRevision;

    /**
     * 具体操作类型，例如旋转、缩放、裁剪框更新。
     */
    private String opType;

    /**
     * 数值型操作值。
     * 旋转时表示 delta，缩放时表示 factor，裁剪框操作时该值可忽略。
     */
    private Double value;

    /**
     * 裁剪框左上角 x。
     */
    private Double cropX;

    /**
     * 裁剪框左上角 y。
     */
    private Double cropY;

    /**
     * 裁剪框宽度。
     */
    private Double cropWidth;

    /**
     * 裁剪框高度。
     */
    private Double cropHeight;

    /**
     * 客户端时间戳，当前版本主要用于调试与排查。
     */
    private Long ts;
}
