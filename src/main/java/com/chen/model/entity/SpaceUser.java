package com.chen.model.entity;

import java.util.Date;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 空间用户关联(SpaceUser)表实体类
 *
 * @author makejava
 * @since 2026-02-23 23:30:02
 */
@SuppressWarnings("serial")
@TableName(value="space_user")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpaceUser implements Serializable {

    private static final long serialVersionUID = -3364792052596583135L;
    //id
    private Long id;
    //空间 id
    private Long spaceId;
    //用户 id
    private Long userId;
    //空间角色：viewer/editor/admin
    private String spaceRole;
    //创建时间
    private Date createTime;
    //更新时间
    private Date updateTime;

}

