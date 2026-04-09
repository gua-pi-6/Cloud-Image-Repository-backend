package com.chen.websocket.interceptor;

import cn.dev33.satoken.exception.NotLoginException;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.chen.constant.PictureCollabConstant;
import com.chen.constant.UserConstant;
import com.chen.manager.auth.SpaceUserAuthManager;
import com.chen.manager.auth.core.StpKit;
import com.chen.manager.auth.model.SpaceUserPermissionConstant;
import com.chen.model.entity.Picture;
import com.chen.model.entity.Space;
import com.chen.model.entity.User;
import com.chen.model.enums.SpaceTypeEnum;
import com.chen.service.PictureService;
import com.chen.service.SpaceService;
import com.chen.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
/**
 * WebSocket 握手阶段的鉴权拦截器。
 *
 * <p>这里的目标不是处理协同业务，而是回答两个问题：
 * 1. 这个连接是不是合法用户；
 * 2. 这个用户有没有权限编辑目标图片。
 *
 * <p>一旦握手成功，后续 handler 就可以直接信任 session attributes，
 *
 */
public class PictureEditHandshakeInterceptor implements HandshakeInterceptor {


    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        // 这里只接受来自 Servlet 容器的标准 HTTP 握手请求。
        if (!(request instanceof ServletServerHttpRequest)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();

        // pictureId 是协同房间的最小上下文，没有它就无法路由到具体图片。
        Long pictureId = parsePictureId(servletRequest);
        if (pictureId == null) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        // 先确认是谁，再确认有没有权限进这个房间。
        User loginUser = userService.getLoginUser(servletRequest);
        if (loginUser == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        if (!hasPictureEditPermission(loginUser, pictureId)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(PictureCollabConstant.WS_SESSION_PICTURE_ID, pictureId);
        attributes.put(PictureCollabConstant.WS_SESSION_USER_ID, loginUser.getId());
        attributes.put(PictureCollabConstant.WS_SESSION_USER_NAME, loginUser.getUserName());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private Long parsePictureId(HttpServletRequest request) {
        String pictureIdString = request.getParameter("pictureId");
        if (StrUtil.isBlank(pictureIdString) || !StrUtil.isNumeric(pictureIdString)) {
            return null;
        }
        return Long.parseLong(pictureIdString);
    }

    private boolean hasPictureEditPermission(User loginUser, Long pictureId) {
        if (loginUser == null || pictureId == null || pictureId <= 0) {
            return false;
        }

        // 第一步：图片必须真实存在。
        Picture picture = pictureService.getById(pictureId);
        if (picture == null) {
            return false;
        }
        Long spaceId = picture.getSpaceId();
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            return false;
        }

        // 非团队空间走“本人或管理员”逻辑。
        if (ObjUtil.equals(space.getSpaceType(), SpaceTypeEnum.PRIVATE.getValue()) || spaceId == 0L) {
            return ObjUtil.equals(picture.getUserId(), loginUser.getId()) || userService.isAdmin(loginUser);
        }

        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        return permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT);
    }
}
