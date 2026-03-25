package com.chen.service.impl;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen.exception.BusinessException;
import com.chen.exception.ErrorCode;
import com.chen.exception.ThrowUtils;
import com.chen.manager.auth.SpaceUserAuthManager;
import com.chen.manager.sharding.DynamicShardingManager;
import com.chen.model.dto.space.SpaceAddRequest;
import com.chen.model.dto.space.SpaceQueryRequest;
import com.chen.model.entity.SpaceUser;
import com.chen.model.entity.User;
import com.chen.model.enums.SpaceLevelEnum;
import com.chen.model.enums.SpaceRoleEnum;
import com.chen.model.enums.SpaceTypeEnum;
import com.chen.model.vo.SpaceVO;
import com.chen.service.SpaceService;
import com.chen.mapper.SpaceMapper;
import com.chen.model.entity.Space;
import com.chen.service.SpaceUserService;
import com.chen.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 空间(Space)表服务实现类
 *
 * @author makejava
 * @since 2026-02-09 21:02:47
 */
@Service("spaceService")
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService {

    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private UserService userService;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;
    @Resource
    @Lazy
    private SpaceUserService spaceUserService;
    @Resource
    @Lazy
    private DynamicShardingManager dynamicShardingManager;


    @Override
    public SpaceVO getSpaceVoById(SpaceQueryRequest spaceQueryRequest, HttpServletRequest request) {
        Long spaceId = spaceQueryRequest.getId();
        Space space = null;
        if (spaceId == null) {
            space = this.lambdaQuery().eq(Space::getUserId, spaceQueryRequest.getUserId())
                    .eq(Space::getSpaceType, spaceQueryRequest.getSpaceType()).one();
        } else {
            space = this.lambdaQuery().eq(Space::getId, spaceId).one();
        }
        ThrowUtils.throwIf(Objects.isNull(space), ErrorCode.NOT_FOUND_ERROR, "用户空间不存在");
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, userService.getLoginUser(request));
        spaceVO.setPermissionList(permissionList);
        return spaceVO;
    }

    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 默认值
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if (spaceAddRequest.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        // 填充数据
        this.fillSpaceBySpaceLevel(space);
        // 数据校验
        this.validSpace(space, true);
        Long userId = loginUser.getId();
        space.setUserId(userId);
        // 权限校验
        if (SpaceLevelEnum.COMMON.getValue() != spaceAddRequest.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        // 针对用户进行加锁
        String lock = String.valueOf(userId).intern();
        synchronized (lock) {
            Long newSpaceId = transactionTemplate.execute(status -> {
                if (!userService.isAdmin(loginUser)) {
                    Integer spaceType = spaceAddRequest.getSpaceType();
                    SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
                    if (spaceTypeEnum != null) {
                        // 校验用户是否已存在指定类型空间
                        boolean exists = this.lambdaQuery().eq(Space::getUserId, userId).eq(Space::getSpaceType, spaceTypeEnum.getValue()).exists();
                        ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户每类空间仅能有一个");
                    }
                    else {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型错误");
                    }
                }
                // 写入数据库
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
                // 如果是团队空间，关联新增团队成员记录
                if (SpaceTypeEnum.TEAM.getValue() == spaceAddRequest.getSpaceType()) {
                    SpaceUser spaceUser = new SpaceUser();
                    spaceUser.setSpaceId(space.getId());
                    spaceUser.setUserId(userId);
                    spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                    result = spaceUserService.save(spaceUser);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                }
                // 创建分表
                dynamicShardingManager.createSpacePictureTable(space);
                // 返回新写入的数据 id
                return space.getId();

            });
            // 返回结果是包装类，可以做一些处理
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }
    }

    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
}



    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
        // 要创建
        if (add) {
            if (spaceType == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不能为空");
            }
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
        }
        // 修改数据时，如果要改空间级别
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (spaceType != null &&  spaceTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
    }

}
