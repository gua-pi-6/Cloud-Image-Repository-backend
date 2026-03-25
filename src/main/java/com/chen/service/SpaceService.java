package com.chen.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chen.model.dto.space.SpaceAddRequest;
import com.chen.model.dto.space.SpaceQueryRequest;
import com.chen.model.entity.Space;
import com.chen.model.entity.User;
import com.chen.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
 * 空间(Space)表服务接口
 *
 * @author makejava
 * @since 2026-02-09 21:02:46
 */
public interface SpaceService extends IService<Space> {

    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    void fillSpaceBySpaceLevel(Space space);

    void validSpace(Space space, boolean add);

    SpaceVO getSpaceVoById(SpaceQueryRequest userId, HttpServletRequest request);
}
