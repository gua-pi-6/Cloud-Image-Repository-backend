package com.chen.controller;




import com.chen.annotation.AuthCheck;
import com.chen.commom.BaseResponse;
import com.chen.commom.ResultUtils;
import com.chen.constant.UserConstant;
import com.chen.exception.BusinessException;
import com.chen.exception.ErrorCode;
import com.chen.exception.ThrowUtils;
import com.chen.model.dto.space.SpaceAddRequest;
import com.chen.model.dto.space.SpaceQueryRequest;
import com.chen.model.dto.space.SpaceUpdateRequest;
import com.chen.model.entity.Space;
import com.chen.model.enums.SpaceLevelEnum;
import com.chen.model.vo.SpaceLevel;
import com.chen.model.vo.SpaceVO;
import com.chen.service.SpaceService;
import com.chen.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * 空间(Space)表控制层
 *
 * @author makejava
 * @since 2026-02-09 21:02:45
 */
@RestController
@RequestMapping("/space")
public class SpaceController {
    @Resource
    private SpaceService spaceService;
    @Resource
    private UserService userService;

    /**
     * 获取用户私人空间详情
     * @return
     */
    @PostMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVo(HttpServletRequest request) {
        Long userId = userService.getLoginUser(request).getId();
        SpaceVO spaceVO = spaceService.getSpaceVoById(userId);
        return ResultUtils.success(spaceVO);
    }

    /**
     * 创建用户私人空间
     * @param spaceAddRequest
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(Objects.isNull(spaceAddRequest), ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceService.addSpace(spaceAddRequest, userService.getLoginUser(request));
        return ResultUtils.success(spaceId);
    }


    /**
     * 获取空间级别列表
     * @return
     */
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values()) // 获取所有枚举
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }


    /**
     * 更新用户私人空间
     * @param spaceUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest) {
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        // 自动填充数据
        spaceService.fillSpaceBySpaceLevel(space);
        // 数据校验
        spaceService.validSpace(space, false);
        // 判断是否存在
        long id = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

}

