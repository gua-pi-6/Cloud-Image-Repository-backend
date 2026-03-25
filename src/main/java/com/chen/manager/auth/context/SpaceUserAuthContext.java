package com.chen.manager.auth.context;

import com.chen.model.entity.Picture;
import com.chen.model.entity.Space;
import com.chen.model.entity.SpaceUser;
import lombok.Data;

import java.io.Serializable;

@Data
public class SpaceUserAuthContext implements Serializable {

    // id
    private Long id;

    // 空间ID
    private Long spaceId;

    // 用户ID
    private Long userId;

    // 空间用户ID
    private Long spaceUserId;

    // 图片ID
    private Long pictureId;

    // 图片信息
    private Picture picture;

    // 空间信息
    private Space space;

    // 空间用户信息
    private SpaceUser spaceUser;
}
