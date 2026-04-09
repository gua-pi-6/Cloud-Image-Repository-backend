package com.chen.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "picture_collab_state")
@Data
/**
 * 图片协同状态快照表。
 *
 * <p>一张图片在任意时刻只保留一份最新状态，
 * 用于新连接快速同步，不需要从日志表全量回放。
 */
public class PictureCollabState implements Serializable {

    /**
     * 直接以 pictureId 作为主键，表示“一图一份快照”。
     */
    @TableId(type = IdType.INPUT)
    private Long pictureId;

    /**
     * 当前快照版本号，每成功应用一次操作就加 1。
     */
    private Long revision;

    private Double angle;

    private Double scale;

    private Double cropX;

    private Double cropY;

    private Double cropWidth;

    private Double cropHeight;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
