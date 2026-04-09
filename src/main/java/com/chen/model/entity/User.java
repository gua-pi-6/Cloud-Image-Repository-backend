package com.chen.model.entity;

import java.util.Date;
import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户(User)表实体类
 *
 * @author makejava
 * @since 2025-12-28 21:49:55
 */
@SuppressWarnings("serial")
@TableName(value="user")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User implements Serializable {

    private static final long serialVersionUID = -3364792052596583135L;

    //id
    // 采用雪花算法
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    //账号
    private String userAccount;
    //密码
    private String userPassword;
    //用户昵称
    private String userName;
    //用户头像
    private String userAvatar;
    //用户简介
    private String userProfile;
    //用户角色：user/admin
    private String userRole;
    //编辑时间
    private Date editTime;
    //创建时间
    private Date createTime;
    //更新时间
    private Date updateTime;
    //是否删除
    @TableLogic
    private Integer isDelete;



}

