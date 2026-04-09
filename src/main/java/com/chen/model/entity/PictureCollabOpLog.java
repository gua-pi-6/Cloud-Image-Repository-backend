package com.chen.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "picture_collab_op_log")
@Data
/**
 * 图片协同操作日志表。
 *
 * <p>这张表的价值主要有三个：
 * 1. 通过 opId 做幂等去重；
 * 2. 保留审计/排错线索；
 * 3. 为后续扩展历史回放能力预留基础。
 */
public class PictureCollabOpLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 客户端操作唯一 id，全局唯一。
     */
    private String opId;

    private Long pictureId;

    private Long userId;

    private Long baseRevision;

    private String opType;

    /**
     * 数值型操作原始值。
     */
    private Double opValue;

    /**
     * 裁剪框操作的原始矩形参数。
     */
    private Double opCropX;

    private Double opCropY;

    private Double opCropWidth;

    private Double opCropHeight;

    /**
     * 服务端应用这次操作后的版本号。
     */
    private Long serverRevision;

    /**
     * 服务端应用完成后的权威结果快照。
     */
    private Double resultAngle;

    private Double resultScale;

    private Double resultCropX;

    private Double resultCropY;

    private Double resultCropWidth;

    private Double resultCropHeight;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
