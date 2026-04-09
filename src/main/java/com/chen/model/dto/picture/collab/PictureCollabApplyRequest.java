package com.chen.model.dto.picture.collab;

import lombok.Data;

@Data
/**
 * 协同服务层的“应用操作请求”。
 *
 * <p>这个对象是协议层和领域层之间的边界：
 * handler 把 WebSocket 消息转换成它，service 只关心业务含义，不再关心传输协议。
 */
public class PictureCollabApplyRequest {

    /**
     * 当前编辑的图片 id。
     */
    private Long pictureId;

    /**
     * 发起操作的用户 id。
     */
    private Long userId;

    /**
     * 客户端操作唯一 id，用于幂等去重。
     */
    private String opId;

    /**
     * 客户端看到的基线版本号。
     */
    private Long baseRevision;

    /**
     * 操作类型。
     */
    private String opType;

    /**
     * 数值型操作值：
     * 旋转时是角度增量，缩放时是倍率。
     */
    private Double value;

    /**
     * 裁剪框参数只在 OP_CROP_BOX 下生效。
     */
    private Double cropX;

    private Double cropY;

    private Double cropWidth;

    private Double cropHeight;
}
