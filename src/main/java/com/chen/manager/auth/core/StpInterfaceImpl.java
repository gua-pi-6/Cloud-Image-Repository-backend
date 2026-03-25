package com.chen.manager.auth.core;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.chen.exception.BusinessException;
import com.chen.exception.ErrorCode;
import com.chen.manager.auth.SpaceUserAuthManager;
import com.chen.manager.auth.context.SpaceUserAuthContext;
import com.chen.model.entity.Picture;
import com.chen.model.entity.Space;
import com.chen.model.entity.SpaceUser;
import com.chen.model.entity.User;
import com.chen.model.enums.SpaceRoleEnum;
import com.chen.model.enums.SpaceTypeEnum;
import com.chen.service.PictureService;
import com.chen.service.SpaceService;
import com.chen.service.SpaceUserService;
import com.chen.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.chen.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 */
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private UserService userService;
    @Resource
    private PictureService pictureService;
    @Resource
    private SpaceUserService spaceUserService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;
    @Value("${server.servlet.context-path}")
    private String contextPath;

    /**
     * 返回一个账号所拥有的权限码集合 
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // loginType不是space,返回空列表
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }
        List<String> adminPermissions = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        SpaceUserAuthContext spaceUserAuthContext = this.getAuthContextByRequest();
        // 如果所有字段为空,视为查询公共图库,返回所有权限
        if (BeanUtil.isEmpty(spaceUserAuthContext)) {
            return adminPermissions;
        }
        // 效验用户是否登录(获取用户id方便后续操作)
        User user = (User)StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        Long userId = user.getId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
        // 优先根据spaceUser获取信息
        SpaceUser logSpaceUser = spaceUserAuthContext.getSpaceUser();
        if (logSpaceUser != null) {
            if (logSpaceUser.getSpaceRole() != null) {
                return spaceUserAuthManager.getPermissionsByRole(logSpaceUser.getSpaceRole());
            }
        }
        // 无 spaceUser 则根据spaceUserId获取信息
        Long spaceUserId = spaceUserAuthContext.getSpaceUserId();
        if (spaceUserId != null) {
            SpaceUser spaceUser = spaceUserService.getById(spaceUserId);
            if (spaceUser == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
            }
            // 效验这条空间成员信息属于当前登录用户
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getUserId, userId)
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    .one();
            if (loginSpaceUser == null) {
                return new ArrayList<>();
            }
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }
        // 无 spaceUser 和 spaceUserId, 则根据 spaceId 和 pictureId 获取信息
        Long spaceId = spaceUserAuthContext.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            if (space == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
            }
            if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
                if (space.getUserId().equals(userId) || userService.isAdmin(user)) {
                    return adminPermissions;
                } else {
                    return new ArrayList<>();
                }
            }
            else {
                SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                        .eq(SpaceUser::getUserId, userId)
                        .eq(SpaceUser::getSpaceId, spaceId)
                        .one();
                if (loginSpaceUser == null) {
                    return new ArrayList<>();
                }
                return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
            }
        }
        Long pictureId = spaceUserAuthContext.getPictureId();
        if (pictureId != null) {
            Picture picture = pictureService.getById(pictureId);
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
            }
            spaceId = picture.getSpaceId();
            if (spaceId == null) {
                if (userId.equals(picture.getUserId()) || userService.isAdmin(user)) {
                    return adminPermissions;
                } else {
                    spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.VIEWER.getValue());
                }
            }
            Space space = spaceService.getById(spaceId);
            if (space == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
            }
            if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
                if (space.getUserId().equals(userId) || userService.isAdmin(user)) {
                    return adminPermissions;
                } else {
                    return new ArrayList<>();
                }
            } else {
                SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                        .eq(SpaceUser::getUserId, userId)
                        .eq(SpaceUser::getSpaceId, spaceId)
                        .one();
                if (loginSpaceUser == null) {
                    return new ArrayList<>();
                }
                return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
            }
        }
        return new ArrayList<>();
    }

    /**
     * 返回一个账号所拥有的角色标识集合
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return new ArrayList<>();
    }

    /**
     * 从请求中获取上下文对象
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;
        // 获取请求参数
        if (ContentType.JSON.getValue().equals(contentType)) {
            String body = ServletUtil.getBody(request);
            authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        } else {
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }
        // 根据请求路径区分 id 字段的含义
        Long id = authRequest.getId();
        if (ObjUtil.isNotNull(id)) {
            // 获取到请求路径的业务前缀，/api/picture/aaa?a=1
            String requestURI = request.getRequestURI();
            // 先替换掉上下文，剩下的就是前缀
            String partURI = requestURI.replace(contextPath + "/", "");
            // 获取前缀的第一个斜杠前的字符串
            String moduleName = StrUtil.subBefore(partURI, "/", false);
            switch (moduleName) {
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        return authRequest;
    }


    /**
     * 从请求中获取上下文
     */
    private SpaceUserAuthContext getSpaceUserAuthContext() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

        SpaceUserAuthContext spaceUserAuthContext;

        if ("GET".equals(request.getMethod())) {
            // GET请求
            Map<String, String> params = ServletUtil.getParamMap(request);
            spaceUserAuthContext = BeanUtil.copyProperties(params, SpaceUserAuthContext.class);
        } else {
            // POST请求
            Object cachedBody = request.getAttribute("cachedRequestBody");
            String body;
            if (cachedBody instanceof String) {
                body = (String) cachedBody;
            } else {
                body = ServletUtil.getBody(request);
            }
            spaceUserAuthContext = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        }
        // 从请求路径中提取模块类型
        String requestURI = request.getRequestURI();
        String moduleType = StrUtil.subBetween(requestURI, contextPath + "/", "/");

        switch (moduleType) {
            case "user" -> spaceUserAuthContext.setUserId(spaceUserAuthContext.getId());
            case "space" -> spaceUserAuthContext.setSpaceId(spaceUserAuthContext.getId());
            case "picture" -> spaceUserAuthContext.setPictureId(spaceUserAuthContext.getId());
            case "spaceUser" -> spaceUserAuthContext.setSpaceUserId(spaceUserAuthContext.getId());
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知模块类型: " + moduleType);
        }

        return spaceUserAuthContext;
    }

}
