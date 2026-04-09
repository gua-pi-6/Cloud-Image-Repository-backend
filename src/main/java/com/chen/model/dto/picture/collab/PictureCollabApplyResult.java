package com.chen.model.dto.picture.collab;

import lombok.Data;

@Data
/**
 * 服务端应用操作后的结果。
 *
 * <p>注意这里返回的不是“客户端原始意图”，而是“服务端确认后的最终权威状态”。
 * 这样客户端可以直接用它覆盖本地状态，避免继续猜测。
 */
public class PictureCollabApplyResult {

    private Long pictureId;

    private Long userId;

    private String opId;

    private String opType;

    /**
     * 本次操作原始值，主要用于 ACK / 广播时保留上下文。
     */
    private Double opValue;

    private Long baseRevision;

    /**
     * 服务端应用完成后的版本号。
     */
    private Long serverRevision;

    /**
     * 服务端权威状态快照。
     */
    private Double angle;

    private Double scale;

    private Double cropX;

    private Double cropY;

    private Double cropWidth;

    private Double cropHeight;

    /**
     * 是否命中了幂等去重。
     * true 表示这次不是新应用，而是重复操作的回放结果。
     */
    private Boolean duplicated;
}
