package com.chen.websocket.model;

import lombok.Data;

import java.io.Serializable;

@Data
/**
 * 服务端发给客户端的统一协同响应消息。
 *
 * <p>同一个对象覆盖 SYNC_STATE / ACK / BROADCAST_OP / ONLINE_COUNT / ERROR / PONG，
 * 这样前后端协议层只需要维护一种基础结构。
 */
public class PictureEditServerMessage implements Serializable {

    /**
     * 消息类型。
     */
    private String type;

    /**
     * 错误码，仅 ERROR 类型使用。
     */
    private Integer code;

    /**
     * 错误消息，仅 ERROR 类型使用。
     */
    private String message;

    private Long pictureId;

    private Long userId;

    private String opId;

    private String opType;

    /**
     * 本次操作携带的原始值。
     */
    private Double value;

    private Long baseRevision;

    /**
     * 服务端应用本次操作后的最终版本号。
     */
    private Long serverRevision;

    /**
     * 当前权威角度。
     */
    private Double angle;

    /**
     * 当前权威缩放。
     */
    private Double scale;

    /**
     * 当前权威裁剪框。
     */
    private Double cropX;

    private Double cropY;

    private Double cropWidth;

    private Double cropHeight;

    /**
     * 当前房间在线人数，仅 ONLINE_COUNT 使用。
     */
    private Integer onlineCount;

    /**
     * 服务端时间戳，便于客户端做时序调试。
     */
    private Long serverTime;
}
