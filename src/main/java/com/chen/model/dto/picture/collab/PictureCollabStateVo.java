package com.chen.model.dto.picture.collab;

import lombok.Data;

@Data
/**
 * 返回给客户端的当前协同快照。
 *
 * <p>新连接进入房间时，会先拿到这个对象对应的 SYNC_STATE。
 */
public class PictureCollabStateVo {

    private Long pictureId;

    /**
     * 当前快照版本号。
     */
    private Long revision;

    private Double angle;

    private Double scale;

    private Double cropX;

    private Double cropY;

    private Double cropWidth;

    private Double cropHeight;
}
