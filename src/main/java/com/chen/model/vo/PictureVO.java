package com.chen.model.vo;

import cn.hutool.json.JSONUtil;
import com.chen.model.entity.Picture;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class PictureVO implements Serializable {

    /** id */
    private Long id;

    /** permission list */
    private List<String> permissionList = new ArrayList<>();

    /** picture url */
    private String url;

    /** picture name */
    private String name;

    /** introduction */
    private String introduction;

    /** tags */
    private List<String> tags;

    /** category */
    private String category;

    /** file size */
    private Long picSize;

    /** width */
    private Integer picWidth;

    /** height */
    private Integer picHeight;

    /** ratio */
    private Double picScale;

    /** format */
    private String picFormat;

    /** creator id */
    private Long userId;

    /** create time */
    private Date createTime;

    /** edit time */
    private Date editTime;

    /** update time */
    private Date updateTime;

    /** creator info */
    private UserVO user;

    /** thumbnail url */
    private String thumbnailUrl;

    /** space id (0 means public gallery) */
    private Long spaceId;

    /** 0 private, 1 team, null for public */
    private Integer spaceType;

    /** review status */
    private Integer reviewStatus;

    private static final long serialVersionUID = 1L;

    /** VO -> entity */
    public static Picture voToObj(PictureVO pictureVO) {
        if (pictureVO == null) {
            return null;
        }
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureVO, picture);
        picture.setTags(JSONUtil.toJsonStr(pictureVO.getTags()));
        return picture;
    }

    /** entity -> VO */
    public static PictureVO objToVo(Picture picture) {
        if (picture == null) {
            return null;
        }
        PictureVO pictureVO = new PictureVO();
        BeanUtils.copyProperties(picture, pictureVO);
        pictureVO.setTags(JSONUtil.toList(picture.getTags(), String.class));
        return pictureVO;
    }
}
