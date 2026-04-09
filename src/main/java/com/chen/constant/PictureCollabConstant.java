package com.chen.constant;

/**
 * 图片协同编辑协议常量。
 *
 * <p>这一层的职责是统一 WebSocket 会话属性、消息类型、操作类型的命名，
 * 让握手拦截器、消息处理器、前后端协议都围绕同一套枚举值协作。
 */
public interface PictureCollabConstant {
    /**
     * 握手成功后，挂在 WebSocket session 上的图片 id。
     */
    String WS_SESSION_PICTURE_ID = "ws_picture_id";

    /**
     * 握手成功后，挂在 WebSocket session 上的当前登录用户 id。
     */
    String WS_SESSION_USER_ID = "ws_user_id";

    /**
     * 当前登录用户名，主要用于后续扩展调试和在线成员展示。
     */
    String WS_SESSION_USER_NAME = "ws_user_name";

    /**
     * 相对旋转：客户端提交的是角度增量，而不是最终角度。
     */
    String OP_ROTATE_DELTA = "ROTATE_DELTA";

    /**
     * 相对缩放：客户端提交的是缩放因子，而不是最终缩放值。
     */
    String OP_SCALE_FACTOR = "SCALE_FACTOR";

    /**
     * 裁剪框更新：这一类操作采用“整块覆盖”的语义。
     */
    String OP_CROP_BOX = "CROP_BOX";

    /**
     * 客户端主动加入房间/请求重同步。
     */
    String MESSAGE_JOIN = "JOIN";

    /**
     * 客户端提交操作。
     */
    String MESSAGE_OP = "OP";

    /**
     * 心跳保活。
     */
    String MESSAGE_PING = "PING";

    /**
     * 服务端返回当前快照状态。
     */
    String MESSAGE_SYNC_STATE = "SYNC_STATE";

    /**
     * 服务端仅回给操作者的确认消息。
     */
    String MESSAGE_ACK = "ACK";

    /**
     * 服务端广播给同房间其他连接的增量更新消息。
     */
    String MESSAGE_BROADCAST_OP = "BROADCAST_OP";

    /**
     * 心跳响应。
     */
    String MESSAGE_PONG = "PONG";

    /**
     * 统一错误消息。
     */
    String MESSAGE_ERROR = "ERROR";

    /**
     * 当前图片房间在线协作者数量。
     */
    String MESSAGE_ONLINE_COUNT = "ONLINE_COUNT";
}
