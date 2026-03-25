package com.chen.service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chen.model.dto.spaceuser.SpaceUserAddRequest;
import com.chen.model.dto.spaceuser.SpaceUserQueryRequest;
import com.chen.model.entity.SpaceUser;
import com.chen.model.vo.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 空间用户关联(SpaceUser)表服务接口
 *
 * @author makejava
 * @since 2026-02-23 23:30:02
 */
public interface SpaceUserService extends IService<SpaceUser> {

    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    void validSpaceUser(SpaceUser spaceUser, boolean add);

    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
}
